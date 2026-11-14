package ca.safehaven.intake

import android.content.Context
import androidx.room.Room
import ca.safehaven.intake.data.SubmitIntakeUseCase
import ca.safehaven.intake.data.backup.SafBackupService
import ca.safehaven.intake.data.db.AppDatabase
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.data.pdf.AndroidIntakePdfWriter
import ca.safehaven.intake.data.staff.SharedPrefsPinStore
import ca.safehaven.intake.domain.IntakeSubmitter
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.kiosk.DeviceOwnerKioskController
import ca.safehaven.intake.staff.BackupService
import ca.safehaven.intake.staff.KioskController
import ca.safehaven.intake.staff.PinStore
import java.io.File
import java.time.Clock

/** Manual dependency container (plan 3.4: the app is small, so no Hilt). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val config = IntakeConfig()
    val clock: Clock = Clock.systemUTC()

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "safehaven.db")
            // Version 1 was the Canadian sign-in scaffold and only ever existed on a developer
            // emulator (never on the shelter's tablet), so it's dropped instead of migrated.
            // From version 2 on, every schema change needs a real Migration: there are signed
            // waivers on the tablet.
            .fallbackToDestructiveMigrationFrom(true, 1)
            .build()
    }

    val fileStore = IntakeFileStore(File(appContext.filesDir, "intake"))

    val submitter: IntakeSubmitter by lazy {
        SubmitIntakeUseCase(
            config = { config },
            pdfWriter = AndroidIntakePdfWriter(
                resources = appContext.resources,
                config = { config },
                shelterName = appContext.getString(R.string.shelter_name),
                appName = appContext.getString(R.string.app_name),
                appVersion = BuildConfig.VERSION_NAME,
            ),
            fileStore = fileStore,
            dao = database.submissionDao(),
            reserveSequenceNumber = database.sequenceCounterDao()::next,
            clock = clock,
        )
    }

    // --- Staff area (phase 4), backup and kiosk (phase 5) ---

    val pinStore: PinStore by lazy { SharedPrefsPinStore(appContext, clock) }

    val backupService: BackupService by lazy {
        SafBackupService(appContext, database.submissionDao(), fileStore, clock)
    }

    val kiosk: KioskController by lazy { DeviceOwnerKioskController(appContext) }

    /** FileProvider authority declared in the manifest. */
    val fileProviderAuthority: String = "${appContext.packageName}.files"
}
