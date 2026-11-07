package ca.safehaven.intake

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.res.stringResource
import ca.safehaven.intake.ui.VisitorApp
import ca.safehaven.intake.ui.intake.IntakeViewModel
import ca.safehaven.intake.ui.theme.SafeHavenTheme

class MainActivity : ComponentActivity() {

    private val viewModel: IntakeViewModel by viewModels {
        IntakeViewModel.factory((application as SafeHavenApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Front desk: keep the screen on while the app is showing (tablet stays plugged in).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Autofill (API 26+) could offer one visitor's details to the next. Android 6/7 has no Autofill.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        setContent {
            SafeHavenTheme {
                VisitorApp(viewModel = viewModel, shelterName = stringResource(R.string.shelter_name))
            }
        }
    }
}
