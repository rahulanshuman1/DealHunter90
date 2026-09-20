package app.linkharvest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.linkharvest.data.Platform
import app.linkharvest.data.Platforms

/**
 * Sits on top of the live WebView (drawn by the caller underneath). Only the small status card
 * consumes touches; everything else falls through to the page so it can be used normally.
 */
@Composable
fun BrowserPanel(
    state: UiState,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onOpenSite: (Platform) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.running) {
                    Text(state.status, style = MaterialTheme.typography.bodyMedium)
                    val progress = if (state.progressTotal == 0) 0f else state.progressDone / state.progressTotal.toFloat()
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Site ${minOf(state.progressDone + 1, state.progressTotal)} of ${state.progressTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.waitingForUser) {
                            Button(onClick = onContinue) { Text("Continue") }
                            OutlinedButton(onClick = onSkip) { Text("Skip site") }
                        }
                        TextButton(onClick = onStop) { Text("Stop") }
                    }
                } else {
                    Text(
                        "Open a site to choose your delivery location or sign in. Your choices are remembered for future runs.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Platforms.all, key = { it.id }) { platform ->
                            AssistChip(onClick = { onOpenSite(platform) }, label = { Text(platform.displayName) })
                        }
                    }
                }
            }
        }
    }
}
