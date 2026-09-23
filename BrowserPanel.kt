package app.linkharvest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.linkharvest.data.Platform
import app.linkharvest.data.Platforms

/**
 * Sits on top of the live WebView (drawn by the caller underneath). Many of these sites ask the
 * person to pick a delivery location before showing products, using their own on-page controls —
 * so this panel must be easy to get out of the way rather than covering part of the page.
 *
 * It starts open, shrinks to a small corner button when the person taps the collapse arrow, and
 * reopens by itself whenever the site needs the person to act (a location, sign-in or CAPTCHA).
 */
@Composable
fun BrowserPanel(
    state: UiState,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onOpenSite: (Platform) -> Unit,
) {
    var collapsed by remember { mutableStateOf(false) }

    // The person may be mid-way through setting a location when this turns true; never take that
    // control away from them silently, but do make sure they see the prompt when it first appears.
    LaunchedEffect(state.waitingForUser) {
        if (state.waitingForUser) collapsed = false
    }

    Box(Modifier.fillMaxSize()) {
        if (collapsed) {
            CollapsedBadge(
                needsAttention = state.waitingForUser,
                onClick = { collapsed = false },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        } else {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            if (state.running) {
                                Text(state.status, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(
                                    "Open a site to choose your delivery location or sign in. Your choices are remembered for future runs.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        TextButton(onClick = { collapsed = true }) {
                            Text("Hide")
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }

                    if (state.running) {
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
}

/** A small, out-of-the-way button that reopens the panel, so the page underneath stays fully usable. */
@Composable
private fun CollapsedBadge(needsAttention: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        color = if (needsAttention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = CircleShape,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = if (needsAttention) "Action needed — tap for options" else "Collection status",
                tint = if (needsAttention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
