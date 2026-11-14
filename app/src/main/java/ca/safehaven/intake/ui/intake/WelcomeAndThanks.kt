package ca.safehaven.intake.ui.intake

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.safehaven.intake.R
import ca.safehaven.intake.ui.theme.SafeHavenTheme

/** @param onLogoLongPress hidden staff entry: a long press on the logo. No visible hint. */
@Composable
fun WelcomeScreen(shelterName: String, onStart: () -> Unit, onLogoLongPress: () -> Unit = {}) {
    val longPress by rememberUpdatedState(onLogoLongPress)
    CenteredColumn {
        Image(
            painter = painterResource(R.drawable.shfa_logo),
            contentDescription = stringResource(R.string.shelter_logo_description),
            contentScale = ContentScale.Fit,
            // On a short screen the logo shrinks first, so the Start button keeps its full size.
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 128.dp)
                // No ripple, no click semantics: nothing tells a visitor the logo does anything.
                .pointerInput(Unit) { detectTapGestures(onLongPress = { longPress() }) },
        )
        Spacer(Modifier.height(24.dp))
        Text(shelterName, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_message),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(56.dp))
        Button(onClick = onStart, modifier = Modifier.heightIn(min = 88.dp).widthIn(min = 320.dp)) {
            Text(stringResource(R.string.welcome_start), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun ThanksScreen(firstName: String, onDone: () -> Unit) {
    CenteredColumn {
        Text(
            stringResource(R.string.thanks_title, firstName),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.thanks_message),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.heightIn(min = 72.dp).widthIn(min = 240.dp)) {
            Text(stringResource(R.string.thanks_done))
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun WelcomePreview() = SafeHavenTheme { WelcomeScreen("Safe Haven for Animals", onStart = {}) }

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun ThanksPreview() = SafeHavenTheme { ThanksScreen("Jane", onDone = {}) }
