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
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneId
import kotlin.random.asKotlinRandom

/** Manual dependency container (plan 3.4: the app is small, so no Hilt). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val config = IntakeConfig()
    private val zone: ZoneId = ZoneId.of(config.timeZoneId)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "safehaven.db").build()
    }

    val fileStore = IntakeFileStore(File(appContext.filesDir, "intake"))

    val submitter: IntakeSubmitter by lazy {
        SubmitIntakeUseCase(
            config = { config },
            pdfWriter = AndroidIntakePdfWriter(
                resources = appContext.resources,
                zone = zone,
                shelterName = appContext.getString(R.string.shelter_name),
                appName = appContext.getString(R.string.app_name),
                deviceLabel = config.deviceLabel,
                appVersion = BuildConfig.VERSION_NAME,
            ),
            fileStore = fileStore,
            dao = database.submissionDao(),
            clock = Clock.systemUTC(),
            zone = zone,
            random = SecureRandom().asKotlinRandom(),
        )
    }
}
