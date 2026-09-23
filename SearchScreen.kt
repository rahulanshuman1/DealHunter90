package app.linkharvest.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.linkharvest.data.Platforms
import app.linkharvest.data.Run
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: UiState,
    onQuery: (String) -> Unit,
    onTogglePlatform: (String) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onMaxScrolls: (Int) -> Unit,
    onMinDiscount: (Int) -> Unit,
    onStart: () -> Unit,
    onOpenRun: (Run) -> Unit,
    onDeleteRun: (Run) -> Unit,
    onClearHistory: () -> Unit,
) {
    val canStart = !state.running && state.query.isNotBlank() && state.selected.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column {
                        Text(
                            "DealHunter90",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Finds product links at ${state.minDiscount}% off or more across eight Indian shopping sites.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text("What are you looking for?") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (canStart) onStart() }),
            )
        }

        item {
            ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sites", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSetAll(state.selected.size != Platforms.all.size) }) {
                            Text(if (state.selected.size == Platforms.all.size) "Clear" else "Select all")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Platforms.all.forEach { platform ->
                            FilterChip(
                                selected = platform.id in state.selected,
                                onClick = { onTogglePlatform(platform.id) },
                                label = { Text(platform.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                                ),
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Minimum discount: ${state.minDiscount}%", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = state.minDiscount.toFloat(),
                        onValueChange = { onMinDiscount(it.toInt()) },
                        valueRange = 50f..95f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    )
                    Text("Scroll depth: ${state.maxScrolls}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = state.maxScrolls.toFloat(),
                        onValueChange = { onMaxScrolls(it.toInt()) },
                        valueRange = 1f..25f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    )
                    Text(
                        "Each step scrolls the results page once to load more products. Higher finds more links but takes longer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Button(
                onClick = onStart,
                enabled = canStart,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (state.running) "Searching…" else "Find ${state.minDiscount}%+ deals",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    "Good to know\n" +
                        "• Discounts this deep are rare, so many searches return nothing. Broad terms like " +
                        "\"clearance\", \"sale\" or a category (\"kurta\", \"headphones\") work best; " +
                        "raise Scroll depth to scan more products.\n" +
                        "• The discount is read from each product card. If a site shows no percentage, it is " +
                        "calculated from price and MRP and marked \"calculated\". Always confirm on the product page.\n" +
                        "• Zepto, Blinkit, Instamart and BigBasket only show products after you pick a delivery " +
                        "location. Open them from the Browser tab once.\n" +
                        "• If a site shows a CAPTCHA, the app pauses so you can solve it. " +
                        "Please follow each site's terms of use.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent runs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearHistory) { Text("Clear all") }
                }
            }
            items(state.history, key = { it.id }) { run ->
                ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(run.query) },
                        supportingContent = {
                            Text(
                                "${run.total} deals at ${run.minDiscount}%+ · " +
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(run.timestampMillis)),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteRun(run) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete run")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onOpenRun(run) },
                    )
                }
            }
        }
    }
}
