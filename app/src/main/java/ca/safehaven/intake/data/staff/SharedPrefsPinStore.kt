package ca.safehaven.intake.data.staff

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import ca.safehaven.intake.staff.PinStore
import java.time.Clock

/**
 * The staff [PinStore], kept in a private SharedPreferences file (the app has no DataStore
 * dependency, and the manifest keeps it out of Android backups). Only a salted PBKDF2 hash is
 * stored. The logic (hashing, lockout) lives in [PinLogic].
 */
class SharedPrefsPinStore private constructor(delegate: PinStore) : PinStore by delegate {
    constructor(context: Context, clock: Clock) : this(
        PinLogic(SharedPrefsPinStorage(context.applicationContext), clock),
    )

    companion object {
        /**
         * Forgets the PIN and any lockout, so the next person into the staff area creates a new
         * one. Only [ca.safehaven.intake.staff.PinResetReceiver] calls this. Waivers are untouched.
         */
        fun forget(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(SharedPrefsPinStorage.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
    }
}

/**
 * Every write uses `commit()`, not `apply()`, so a wrong-attempt count is on disk before the
 * result is shown and a crash or power cut can't skip one.
 */
internal class SharedPrefsPinStorage(context: Context) : PinStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readCredential(): PinCredential? {
        val algorithm = prefs.getString(KEY_ALGORITHM, null) ?: return null
        val iterations = prefs.getInt(KEY_ITERATIONS, 0)
        val salt = prefs.getString(KEY_SALT, null)?.let(::decode) ?: return null
        val hash = prefs.getString(KEY_HASH, null)?.let(::decode) ?: return null
        if (iterations <= 0 || salt.isEmpty() || hash.isEmpty()) return null
        return PinCredential(algorithm, iterations, salt, hash)
    }

    override fun readAttempts(): PinAttempts = PinAttempts(
        failedAttempts = prefs.getInt(KEY_FAILED, 0),
        lockoutCount = prefs.getInt(KEY_LOCKOUTS, 0),
        lockedUntilMillis = prefs.getLong(KEY_LOCKED_UNTIL, 0L),
    )

    override fun writeCredential(credential: PinCredential) {
        val ok = prefs.edit()
            .putString(KEY_ALGORITHM, credential.algorithm)
            .putInt(KEY_ITERATIONS, credential.iterations)
            .putString(KEY_SALT, encode(credential.salt))
            .putString(KEY_HASH, encode(credential.hash))
            .putAttempts(PinAttempts.NONE)
            .commit()
        if (!ok) throw PinStorageException()
    }

    override fun writeAttempts(attempts: PinAttempts) {
        if (!prefs.edit().putAttempts(attempts).commit()) throw PinStorageException()
    }

    private fun SharedPreferences.Editor.putAttempts(attempts: PinAttempts) = this
        .putInt(KEY_FAILED, attempts.failedAttempts)
        .putInt(KEY_LOCKOUTS, attempts.lockoutCount)
        .putLong(KEY_LOCKED_UNTIL, attempts.lockedUntilMillis)

    // android.util.Base64: java.util.Base64 is API 26+.
    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray? =
        try {
            Base64.decode(text, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        const val PREFS_NAME = "staff_pin"
        const val KEY_ALGORITHM = "algorithm"
        const val KEY_ITERATIONS = "iterations"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKOUTS = "lockout_count"
        const val KEY_LOCKED_UNTIL = "locked_until_millis"
    }
}
