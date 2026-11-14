package ca.safehaven.intake.data.staff

import ca.safehaven.intake.staff.PinCheck
import ca.safehaven.intake.staff.PinStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinLogicTest {

    private class MutableClock(var now: Instant = Instant.parse("2026-11-09T17:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advanceSeconds(seconds: Long) {
            now = now.plusSeconds(seconds)
        }
    }

    private class InMemoryStorage : PinStorage {
        var credential: PinCredential? = null
        var attempts = PinAttempts.NONE
        override fun readCredential() = credential
        override fun readAttempts() = attempts
        override fun writeCredential(credential: PinCredential) {
            this.credential = credential
            attempts = PinAttempts.NONE
        }
        override fun writeAttempts(attempts: PinAttempts) {
            this.attempts = attempts
        }
    }

    /** The real PBKDF2, with a call counter. */
    private class CountingHasher : PinHasher {
        var calls = 0
        override fun hash(pin: String, salt: ByteArray, iterations: Int, algorithm: String, keyBits: Int): ByteArray {
            calls++
            return Pbkdf2PinHasher.hash(pin, salt, iterations, algorithm, keyBits)
        }
    }

    private val fastParams = PinHashParams.CURRENT.copy(iterations = 1_000)
    private val clock = MutableClock()
    private val storage = InMemoryStorage()
    private val hasher = CountingHasher()

    private fun TestScope.store(params: PinHashParams = fastParams) = PinLogic(
        storage = storage,
        clock = clock,
        hasher = hasher,
        params = params,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private suspend fun PinLogic.failTimes(n: Int): PinCheck {
        var last: PinCheck = PinCheck.NoPin
        repeat(n) { last = verify("9999") }
        return last
    }

    @Test fun noPinUntilOneIsSet() = runTest {
        val store = store()
        assertFalse(store.hasPin())
        assertEquals(PinCheck.NoPin, store.verify("1234"))
        store.setPin("1234")
        assertTrue(store.hasPin())
    }

    @Test fun storesSaltedHashNotThePin() = runTest {
        store().setPin("1234")
        val cred = checkNotNull(storage.credential)
        assertEquals("PBKDF2WithHmacSHA1", cred.algorithm)
        assertEquals(1_000, cred.iterations)
        assertEquals(16, cred.salt.size)
        assertEquals(20, cred.hash.size)
        assertFalse(cred.hash.contentEquals("1234".toByteArray()))

        val firstSalt = cred.salt
        store().setPin("1234")
        assertFalse("A new salt each time", firstSalt.contentEquals(storage.credential!!.salt))
    }

    @Test fun rejectsMalformedPins() = runTest {
        val store = store()
        for (bad in listOf("", "123", "123456789", "12a4", "12 34", "١٢٣٤")) {
            try {
                store.setPin(bad)
                fail("Accepted a bad PIN")
            } catch (expected: IllegalArgumentException) {
                // Expected.
            }
        }
        assertFalse(store.hasPin())
        store.setPin("1234")
        store.setPin("12345678")
    }

    @Test fun correctPinIsOk() = runTest {
        val store = store()
        store.setPin("2468")
        assertEquals(PinCheck.Ok, store.verify("2468"))
        assertEquals(PinCheck.Ok, store.verify("2468"))
    }

    @Test fun wrongPinCountsDownThenLocksOutFor30Seconds() = runTest {
        val store = store()
        store.setPin("2468")
        assertEquals(PinCheck.Wrong(4), store.verify("1111"))
        assertEquals(PinCheck.Wrong(3), store.verify("1111"))
        assertEquals(PinCheck.Wrong(2), store.verify("1111"))
        assertEquals(PinCheck.Wrong(1), store.verify("1111"))
        assertEquals(PinCheck.LockedOut(30), store.verify("1111"))
    }

    @Test fun lockedOutDoesNotHashAndRejectsEvenTheRightPin() = runTest {
        val store = store()
        store.setPin("2468")
        store.failTimes(PinStore.MAX_ATTEMPTS)
        val callsBefore = hasher.calls

        clock.advanceSeconds(10)
        assertEquals(PinCheck.LockedOut(20), store.verify("2468"))
        assertEquals(callsBefore, hasher.calls)

        clock.advanceSeconds(20)
        assertEquals(PinCheck.Ok, store.verify("2468"))
    }

    @Test fun lockoutsDoubleAndCapAt15Minutes() = runTest {
        val store = store()
        store.setPin("2468")
        val expected = listOf(30L, 60L, 120L, 240L, 480L, 900L, 900L)
        for (seconds in expected) {
            assertEquals(PinCheck.LockedOut(seconds), store.failTimes(PinStore.MAX_ATTEMPTS))
            clock.advanceSeconds(seconds)
        }
        // After a lockout ends the countdown starts again from the top.
        assertEquals(PinCheck.Wrong(4), store.verify("1111"))
    }

    @Test fun correctPinResetsFailuresAndLockoutHistory() = runTest {
        val store = store()
        store.setPin("2468")
        store.failTimes(PinStore.MAX_ATTEMPTS)
        clock.advanceSeconds(30)
        store.failTimes(3)
        assertEquals(PinCheck.Ok, store.verify("2468"))
        assertEquals(PinAttempts.NONE, storage.attempts)
        // Back to a 30 s first lockout.
        assertEquals(PinCheck.LockedOut(30), store.failTimes(PinStore.MAX_ATTEMPTS))
    }

    @Test fun setPinClearsLockout() = runTest {
        val store = store()
        store.setPin("2468")
        store.failTimes(PinStore.MAX_ATTEMPTS)
        store.setPin("1357")
        assertEquals(PinCheck.Ok, store.verify("1357"))
        assertEquals(PinCheck.Wrong(4), store.verify("2468"))
    }

    @Test fun lockoutSurvivesARestart() = runTest {
        store().setPin("2468")
        store().failTimes(PinStore.MAX_ATTEMPTS)
        // A new instance on the same storage, as after a reboot.
        clock.advanceSeconds(5)
        assertEquals(PinCheck.LockedOut(25), store().verify("2468"))
    }

    @Test fun clockSetBackCannotStretchTheLockoutPast15Minutes() = runTest {
        val store = store()
        store.setPin("2468")
        store.failTimes(PinStore.MAX_ATTEMPTS)
        clock.advanceSeconds(-24L * 3600)
        assertEquals(PinCheck.LockedOut(900), store.verify("2468"))
        clock.advanceSeconds(900)
        assertEquals(PinCheck.Ok, store.verify("2468"))
    }

    @Test fun failureIsCountedBeforeHashing() = runTest {
        // Throws on every hash once a PIN exists, like the app being killed mid-hash.
        val crashingHasher = PinHasher { pin, salt, iterations, algorithm, keyBits ->
            if (storage.credential != null) throw IllegalStateException("app killed mid-hash")
            Pbkdf2PinHasher.hash(pin, salt, iterations, algorithm, keyBits)
        }
        val store = PinLogic(storage, clock, crashingHasher, fastParams, dispatcher = UnconfinedTestDispatcher(testScheduler))
        store.setPin("2468")
        try {
            store.verify("1111")
            fail()
        } catch (expected: IllegalStateException) {
            // Expected.
        }
        assertEquals(1, storage.attempts.failedAttempts)
    }

    @Test fun leftoverMaxFailuresStartALockout() = runTest {
        val store = store()
        store.setPin("2468")
        storage.attempts = PinAttempts(failedAttempts = PinStore.MAX_ATTEMPTS, lockoutCount = 0, lockedUntilMillis = 0)
        assertEquals(PinCheck.LockedOut(30), store.verify("2468"))
    }

    @Test fun oldParametersAreUpgradedOnCorrectPin() = runTest {
        store(fastParams.copy(iterations = 500)).setPin("2468")
        val oldHash = storage.credential!!.hash

        val upgraded = store(fastParams)
        assertEquals(PinCheck.Wrong(4), upgraded.verify("1111"))
        assertEquals(500, storage.credential!!.iterations)
        assertEquals(PinCheck.Ok, upgraded.verify("2468"))
        assertEquals(1_000, storage.credential!!.iterations)
        assertFalse(oldHash.contentEquals(storage.credential!!.hash))
        assertEquals(PinCheck.Ok, upgraded.verify("2468"))
    }

    @Test fun pbkdf2MatchesAKnownVector() {
        // RFC 6070, test case 2: "password" / "salt", 2 iterations, 20 bytes.
        val hash = Pbkdf2PinHasher.hash("password", "salt".toByteArray(), 2, "PBKDF2WithHmacSHA1", 160)
        val expected = "ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, hash)
    }

    @Test fun lockoutDurations() {
        assertEquals(30_000L, PinLogic.lockoutDurationMillis(0))
        assertEquals(60_000L, PinLogic.lockoutDurationMillis(1))
        assertEquals(480_000L, PinLogic.lockoutDurationMillis(4))
        assertEquals(900_000L, PinLogic.lockoutDurationMillis(5))
        assertEquals(900_000L, PinLogic.lockoutDurationMillis(100))
    }
}
