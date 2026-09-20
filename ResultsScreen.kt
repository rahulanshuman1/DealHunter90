package app.linkharvest.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.linkharvest.data.Platforms

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultsScreen(
    state: UiState,
    onOpenLink: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
) {
    val platformsShown = Platforms.all.filter { it.id in state.results || it.id in state.errors }

    if (platformsShown.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                if (state.running) "Searching… results will appear here." else "No results yet. Run a search first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Padded {
                Text(
                    "${state.totalLinks} deals at ${state.resultsMinDiscount}%+ off for “${state.resultsQuery}”",
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = onCopyAll, enabled = state.totalLinks > 0) { Text("Copy") }
                    OutlinedButton(onClick = onShare, enabled = state.totalLinks > 0) { Text("Share") }
                    OutlinedButton(onClick = onExport, enabled = state.totalLinks > 0) { Text("Export CSV") }
                }
                Text(
                    "Tap a link to open it · long-press to copy. Confirm the price on the product page before buying.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        platformsShown.forEach { platform ->
            val deals = state.results[platform.id].orEmpty()
            val error = state.errors[platform.id]
            val scanned = state.scanned[platform.id] ?: 0
            item(key = "header-${platform.id}") {
                Padded {
                    HorizontalDivider(Modifier.padding(bottom = 12.dp))
                    Text("${platform.displayName} · ${deals.size}", style = MaterialTheme.typography.titleMedium)
                    when {
                        error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        deals.isEmpty() && scanned == 0 && platform.needsLocation -> Text(
                            "No products found. Set a delivery location in the Browser tab and try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        deals.isEmpty() -> Text(
                            "Scanned $scanned products; none at ${state.resultsMinDiscount}%+ off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Text(
                            "Scanned $scanned products.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(deals, key = { "${platform.id}|${it.url}" }) { deal ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onOpenLink(deal.url) }, onLongClick = { onCopyLink(deal.url) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (deal.computed) "${deal.percent}% off (calculated)" else "${deal.percent}% off",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = deal.url.removePrefix("https://"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (deal.snippet.isNotBlank()) {
                        Text(
                            text = deal.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        content()
    }
}
