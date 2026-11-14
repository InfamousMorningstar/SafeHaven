package ca.safehaven.intake.data.staff

import ca.safehaven.intake.staff.PinCheck
import ca.safehaven.intake.staff.PinStore
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * The staff PIN logic, free of Android classes so it runs in JVM unit tests. SharedPrefsPinStore
 * plugs it into SharedPreferences.
 *
 * Nothing here logs: not the PIN, not the hash, not the attempt count.
 */

/** A stored PIN hash plus everything needed to recompute it (so the parameters can change later). */
internal class PinCredential(
    /** A `SecretKeyFactory` algorithm name, e.g. `PBKDF2WithHmacSHA1`. */
    val algorithm: String,
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
)

/** Wrong-attempt bookkeeping. [lockedUntilMillis] is wall-clock time, so a reboot doesn't clear it. */
internal data class PinAttempts(
    /** Wrong tries since the last lockout (or the last correct PIN). */
    val failedAttempts: Int,
    /** Lockouts since the last correct PIN; each one doubles the next lockout. */
    val lockoutCount: Int,
    val lockedUntilMillis: Long,
) {
    companion object {
        val NONE = PinAttempts(failedAttempts = 0, lockoutCount = 0, lockedUntilMillis = 0L)
    }
}

/** Durable storage for the PIN. Every write must be on disk when it returns (no lazy writes). */
internal interface PinStorage {
    fun readCredential(): PinCredential?
    fun readAttempts(): PinAttempts

    /** Replaces the credential and clears all attempts and lockouts, in one write. */
    fun writeCredential(credential: PinCredential)

    fun writeAttempts(attempts: PinAttempts)
}

internal fun interface PinHasher {
    fun hash(pin: String, salt: ByteArray, iterations: Int, algorithm: String, keyBits: Int): ByteArray
}

/** PBKDF2 through the platform's `SecretKeyFactory`. */
internal object Pbkdf2PinHasher : PinHasher {
    override fun hash(pin: String, salt: ByteArray, iterations: Int, algorithm: String, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyBits)
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Parameters for newly created hashes. Existing hashes keep theirs until the next correct PIN. */
internal data class PinHashParams(
    val algorithm: String,
    val iterations: Int,
    val keyBits: Int,
    val saltBytes: Int,
) {
    companion object {
        /**
         * `PBKDF2WithHmacSHA1` because it exists on API 23 (`...HmacSHA256` needs API 26, and the
         * tablet runs 6/7). 160-bit output: asking SHA-1 PBKDF2 for more than 160 bits doubles the
         * work for the defender only.
         *
         * 50,000 iterations: on Android 6/7 this algorithm runs in the Bouncy Castle provider (Java
         * code). A Cortex-A53 at 1.5 GHz (Snapdragon 617) manages very roughly 3-8 µs per HMAC-SHA1
         * iteration there, so this is about 150-400 ms. That's an estimate: time it on the real
         * tablet with a release build and adjust. Changing this is safe; old hashes are upgraded
         * on the next correct PIN.
         *
         * With 4-8 digits the hash alone can't stop an offline brute force by someone who has
         * the app's private files; the hash keeps the PIN out of plain sight and the lockout is
         * what stops guessing at the screen.
         */
        val CURRENT = PinHashParams(
            algorithm = "PBKDF2WithHmacSHA1",
            iterations = 50_000,
            keyBits = 160,
            saltBytes = 16,
        )
    }
}

internal class PinLogic(
    private val storage: PinStorage,
    private val clock: Clock,
    private val hasher: PinHasher = Pbkdf2PinHasher,
    private val params: PinHashParams = PinHashParams.CURRENT,
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PinStore {

    /** One verify/setPin at a time, so two quick taps can't both read the same attempt count. */
    private val mutex = Mutex()

    override fun hasPin(): Boolean = storage.readCredential() != null

    override suspend fun setPin(pin: String) {
        require(isValidPin(pin)) {
            "The PIN must be ${PinStore.PIN_MIN_LENGTH} to ${PinStore.PIN_MAX_LENGTH} digits"
        }
        mutex.withLock {
            withContext(dispatcher) { storage.writeCredential(newCredential(pin)) }
        }
    }

    override suspend fun verify(pin: String): PinCheck = mutex.withLock {
        withContext(dispatcher) { verifyLocked(pin) }
    }

    private fun verifyLocked(pin: String): PinCheck {
        val credential = storage.readCredential() ?: return PinCheck.NoPin
        val now = clock.millis()
        var attempts = storage.readAttempts()

        if (attempts.lockedUntilMillis > now) {
            var remaining = attempts.lockedUntilMillis - now
            if (remaining > MAX_LOCKOUT_MS) {
                // The clock was set back. Don't let that stretch a lockout past the maximum.
                remaining = MAX_LOCKOUT_MS
                storage.writeAttempts(attempts.copy(lockedUntilMillis = now + MAX_LOCKOUT_MS))
            }
            return PinCheck.LockedOut(ceilSeconds(remaining))
        }

        if (attempts.failedAttempts >= PinStore.MAX_ATTEMPTS) {
            // The app died between counting the last wrong try and starting the lockout.
            return startLockout(attempts, now)
        }

        // Count the try *before* the slow hash: killing the app mid-hash can't skip a count.
        val failed = attempts.failedAttempts + 1
        attempts = attempts.copy(failedAttempts = failed)
        storage.writeAttempts(attempts)

        if (matches(pin, credential)) {
            if (credential.algorithm != params.algorithm || credential.iterations != params.iterations) {
                storage.writeCredential(newCredential(pin)) // Also clears the attempts.
            } else {
                storage.writeAttempts(PinAttempts.NONE)
            }
            return PinCheck.Ok
        }

        if (failed >= PinStore.MAX_ATTEMPTS) return startLockout(attempts, now)
        return PinCheck.Wrong(attemptsLeft = PinStore.MAX_ATTEMPTS - failed)
    }

    private fun startLockout(attempts: PinAttempts, now: Long): PinCheck.LockedOut {
        val duration = lockoutDurationMillis(attempts.lockoutCount)
        storage.writeAttempts(
            PinAttempts(
                failedAttempts = 0,
                lockoutCount = attempts.lockoutCount + 1,
                lockedUntilMillis = now + duration,
            ),
        )
        return PinCheck.LockedOut(ceilSeconds(duration))
    }

    private fun matches(pin: String, credential: PinCredential): Boolean {
        // A malformed PIN can never match; skip the hash (an empty password can also make some
        // providers throw).
        if (!isValidPin(pin)) return false
        val computed = hasher.hash(
            pin,
            credential.salt,
            credential.iterations,
            credential.algorithm,
            credential.hash.size * 8,
        )
        return MessageDigest.isEqual(computed, credential.hash)
    }

    private fun newCredential(pin: String): PinCredential {
        val salt = randomBytes(params.saltBytes)
        val hash = hasher.hash(pin, salt, params.iterations, params.algorithm, params.keyBits)
        return PinCredential(params.algorithm, params.iterations, salt, hash)
    }

    companion object {
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L

        /** 30 s, 60 s, 120 s, ... capped at 15 minutes. */
        fun lockoutDurationMillis(previousLockouts: Int): Long {
            if (previousLockouts >= 10) return MAX_LOCKOUT_MS // 30 s * 2^10 is far past the cap.
            return minOf(BASE_LOCKOUT_MS shl previousLockouts.coerceAtLeast(0), MAX_LOCKOUT_MS)
        }

        /** ASCII digits only (not other Unicode digits), within the allowed length. */
        fun isValidPin(pin: String): Boolean =
            pin.length in PinStore.PIN_MIN_LENGTH..PinStore.PIN_MAX_LENGTH && pin.all { it in '0'..'9' }

        private fun ceilSeconds(millis: Long): Long = (millis + 999) / 1000
    }
}

private val secureRandom by lazy { SecureRandom() }

internal fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { secureRandom.nextBytes(it) }

/** Thrown when a PIN write didn't reach the disk. */
internal class PinStorageException : IOException("Could not save the staff PIN state")
