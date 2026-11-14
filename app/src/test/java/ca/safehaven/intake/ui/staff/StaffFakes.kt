package ca.safehaven.intake.ui.staff

import android.content.Intent
import android.net.Uri
import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.staff.BackupResult
import ca.safehaven.intake.staff.BackupService
import ca.safehaven.intake.staff.BackupStatus
import ca.safehaven.intake.staff.PinCheck
import ca.safehaven.intake.staff.PinStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory PIN store with the real lockout rule: [PinStore.MAX_ATTEMPTS] wrong tries. */
class FakePinStore(
    var pin: String? = null,
    /** Simulates slow PBKDF2. */
    var verifyMillis: Long = 0,
    var lockoutSeconds: Long = 30,
) : PinStore {
    var failures = 0
    var failNextSave = false
    val verified = mutableListOf<String>()

    override fun hasPin(): Boolean = pin != null

    override suspend fun setPin(pin: String) {
        require(pin.length in PinStore.PIN_MIN_LENGTH..PinStore.PIN_MAX_LENGTH && pin.all { it in '0'..'9' })
        if (failNextSave) {
            failNextSave = false
            throw IllegalStateException("disk full")
        }
        this.pin = pin
        failures = 0
    }

    override suspend fun verify(pin: String): PinCheck {
        verified += pin
        delay(verifyMillis)
        val saved = this.pin ?: return PinCheck.NoPin
        if (failures >= PinStore.MAX_ATTEMPTS) return PinCheck.LockedOut(lockoutSeconds)
        if (pin == saved) {
            failures = 0
            return PinCheck.Ok
        }
        failures++
        return if (failures >= PinStore.MAX_ATTEMPTS) {
            PinCheck.LockedOut(lockoutSeconds)
        } else {
            PinCheck.Wrong(PinStore.MAX_ATTEMPTS - failures)
        }
    }
}

/** Mirrors the Room queries closely enough for the ViewModels. */
class FakeSubmissionDao(initial: List<SubmissionEntity> = emptyList()) : SubmissionDao {
    val rows = MutableStateFlow(initial)
    val searches = mutableListOf<Pair<String, String>>()
    val exported = mutableListOf<String>()

    override suspend fun insert(submission: SubmissionEntity) = rows.update { it + submission }

    override fun observeAll(): Flow<List<SubmissionEntity>> = rows.map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun countByPdfPath(pdfPath: String): Int = rows.value.count { it.pdfPath == pdfPath }

    override suspend fun markAllPendingBackup() = rows.update { list -> list.map { it.copy(backedUpAt = null) } }

    override suspend fun getById(id: String): SubmissionEntity? = rows.value.firstOrNull { it.id == id }

    override fun search(query: String, digits: String): Flow<List<SubmissionEntity>> {
        searches += query to digits
        return rows.map { list ->
            list.filter { r ->
                query.isEmpty() ||
                    r.fullName.contains(query, ignoreCase = true) ||
                    r.guardianName?.contains(query, ignoreCase = true) == true ||
                    r.email?.contains(query, ignoreCase = true) == true ||
                    r.recordCode.contains(query) ||
                    (digits.isNotEmpty() && r.phoneE164.contains(digits))
            }.sortedByDescending { it.sequenceNumber }
        }
    }

    override suspend fun deleteById(id: String): Int {
        val before = rows.value.size
        rows.update { list -> list.filterNot { it.id == id } }
        return before - rows.value.size
    }

    override suspend fun pendingBackup(): List<SubmissionEntity> =
        rows.value.filter { it.backedUpAt == null }.sortedBy { it.sequenceNumber }

    override fun observePendingBackupCount(): Flow<Int> = rows.map { list -> list.count { it.backedUpAt == null } }

    override fun observeLastBackupAt(): Flow<Instant?> = rows.map { list -> list.mapNotNull { it.backedUpAt }.maxOrNull() }

    override suspend fun markBackedUp(id: String, at: Instant) =
        rows.update { list -> list.map { if (it.id == id) it.copy(backedUpAt = at) else it } }

    override suspend fun markExported(id: String, at: Instant) {
        exported += id
        rows.update { list -> list.map { if (it.id == id) it.copy(exportedAt = at) else it } }
    }
}

class FakeBackupService(
    initial: BackupStatus = BackupStatus(folderName = "Waivers", pendingCount = 2, lastBackupAt = null, isRunning = false),
) : BackupService {
    val status = MutableStateFlow(initial)
    var result: BackupResult = BackupResult.Done(2)
    var throwOnBackup: Exception? = null
    var backupMillis: Long = 0
    var backupCalls = 0

    override fun observeStatus(): Flow<BackupStatus> = status

    override fun folderPickerIntent(): Intent = Intent()

    override suspend fun setFolder(treeUri: Uri) = Unit

    override suspend fun backUpNow(): BackupResult {
        backupCalls++
        delay(backupMillis)
        throwOnBackup?.let { throw it }
        return result
    }
}

/** Made-up waiver rows. Never real people (the repo is public). */
object StaffFixtures {
    fun entity(
        sequence: Long,
        fullName: String = "Jane Smith",
        phoneE164: String = "+16025550123",
        isMinor: Boolean = false,
        guardianName: String? = null,
        backedUpAt: Instant? = null,
    ): SubmissionEntity {
        val code = sequence.toString().padStart(4, '0')
        return SubmissionEntity(
            id = "id-$sequence",
            sequenceNumber = sequence,
            recordCode = code,
            createdAt = Instant.parse("2026-11-09T17:00:00Z").plusSeconds(sequence),
            signedOn = LocalDate.of(2026, 11, 9),
            fullName = fullName,
            phoneE164 = phoneE164,
            email = null,
            city = "Phoenix",
            state = "AZ",
            zip = "85001",
            reason = "VISITING",
            dateOfBirth = LocalDate.of(1990, 3, 15),
            isMinor = isMinor,
            guardianName = guardianName,
            waiverVersion = "1",
            pdfPath = "2026/11/2026-11-09_$code.pdf",
            pdfSha256 = "0".repeat(64),
            backedUpAt = backedUpAt,
        )
    }
}
