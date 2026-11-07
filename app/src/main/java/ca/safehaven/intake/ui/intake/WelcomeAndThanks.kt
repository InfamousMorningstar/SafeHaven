package ca.safehaven.intake.ui.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.safehaven.intake.R
import ca.safehaven.intake.ui.theme.SafeHavenTheme

@Composable
fun WelcomeScreen(shelterName: String, onStart: () -> Unit) {
    CenteredColumn {
        // TODO(client): shelter logo above the name. Long-press it for the staff PIN (phase 4).
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
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun WelcomePreview() = SafeHavenTheme { WelcomeScreen("Calgary Cat Shelter", onStart = {}) }

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun ThanksPreview() = SafeHavenTheme { ThanksScreen("Jane", onDone = {}) }
