package ca.safehaven.intake

import android.content.Context
import androidx.room.Room
import ca.safehaven.intake.data.SubmitIntakeUseCase
import ca.safehaven.intake.data.db.AppDatabase
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.data.pdf.AndroidIntakePdfWriter
import ca.safehaven.intake.domain.IntakeSubmitter
import ca.safehaven.intake.domain.model.IntakeConfig
import java.io.File
import java.time.Clock

/** Manual dependency container (plan 3.4: the app is small, so no Hilt). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val config = IntakeConfig()

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "safehaven.db")
            // Version 1 (the Canadian sign-in scaffold) only ever existed on a developer
            // emulator and was never released, so there is nothing to keep: drop it rather than
            // write a migration. Only version 1 is listed, so any later schema change without a
            // Migration fails loudly instead of silently wiping visitors' waivers.
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
            clock = Clock.systemUTC(),
        )
    }
}
