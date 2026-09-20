package app.linkharvest.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.linkharvest.data.CsvExporter
import app.linkharvest.data.Deal
import app.linkharvest.data.Platform
import app.linkharvest.data.Platforms
import app.linkharvest.data.Run
import app.linkharvest.data.RunStore
import app.linkharvest.engine.Interruption
import app.linkharvest.engine.WebCollector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class Tab(val label: String) { Search("Search"), Browser("Browser"), Results("Results") }

data class UiState(
    val query: String = "",
    val selected: Set<String> = Platforms.all.map { it.id }.toSet(),
    val maxScrolls: Int = 8,
    val minDiscount: Int = 90,
    val tab: Tab = Tab.Search,

    val running: Boolean = false,
    val status: String = "",
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val waitingForUser: Boolean = false,

    val resultsQuery: String = "",
    val resultsMinDiscount: Int = 90,
    val results: Map<String, List<Deal>> = emptyMap(),
    val scanned: Map<String, Int> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val history: List<Run> = emptyList(),
) {
    val totalLinks: Int get() = results.values.sumOf { it.size }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RunStore(File(app.filesDir, "runs.json"))
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot messages for a snackbar. */
    val messages = _messages.receiveAsFlow()

    private var web: WebView? = null
    private var collector: WebCollector? = null
    private var job: Job? = null
    private var userGate: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { store.load() }
            _ui.update { it.copy(history = history) }
        }
    }

    // --- WebView lifecycle -------------------------------------------------------------------

    fun bind(webView: WebView) {
        web = webView
        collector = WebCollector(webView)
    }

    fun unbind() {
        job?.cancel() // a run cannot outlive its WebView; partial results are still saved
        collector = null
        web = null
    }

    fun canGoBack(): Boolean = web?.canGoBack() == true
    fun goBack() { web?.goBack() }

    // --- Form state --------------------------------------------------------------------------

    fun setQuery(value: String) = _ui.update { it.copy(query = value.take(120)) }
    fun setMaxScrolls(value: Int) = _ui.update { it.copy(maxScrolls = value.coerceIn(1, 25)) }
    fun setMinDiscount(value: Int) = _ui.update { it.copy(minDiscount = value.coerceIn(50, 95)) }
    fun selectTab(tab: Tab) = _ui.update { it.copy(tab = tab) }

    fun togglePlatform(id: String) = _ui.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun setAllPlatforms(all: Boolean) = _ui.update {
        it.copy(selected = if (all) Platforms.all.map { p -> p.id }.toSet() else emptySet())
    }

    /** Opens a site in the visible browser, e.g. to choose a delivery location or sign in. */
    fun openSite(platform: Platform) {
        if (_ui.value.running) return
        web?.loadUrl(platform.homeUrl)
        selectTab(Tab.Browser)
    }

    // --- Run control -------------------------------------------------------------------------

    fun start() {
        val state = _ui.value
        val query = state.query.trim()
        val platforms = Platforms.all.filter { it.id in state.selected }
        val activeCollector = collector
        if (state.running || query.isEmpty() || platforms.isEmpty() || activeCollector == null) return

        val minDiscount = state.minDiscount
        job = viewModelScope.launch {
            val results = LinkedHashMap<String, List<Deal>>()
            val scanned = LinkedHashMap<String, Int>()
            val errors = LinkedHashMap<String, String>()
            _ui.update {
                it.copy(
                    running = true, tab = Tab.Browser, status = "Starting…",
                    resultsQuery = query, resultsMinDiscount = minDiscount,
                    results = emptyMap(), scanned = emptyMap(), errors = emptyMap(),
                    progressDone = 0, progressTotal = platforms.size,
                )
            }
            try {
                platforms.forEachIndexed { index, platform ->
                    _ui.update {
                        it.copy(progressDone = index, status = "Opening ${platform.displayName}…")
                    }
                    try {
                        val products = activeCollector.collect(
                            platform = platform,
                            query = query,
                            maxScrolls = state.maxScrolls,
                            minDiscount = minDiscount,
                            onProgress = { seen, matched ->
                                _ui.update {
                                    it.copy(status = "${platform.displayName}: $seen products scanned, $matched at $minDiscount%+ off")
                                }
                            },
                            onNeedsUser = { reason -> waitForUser(platform, reason) },
                        )
                        scanned[platform.id] = products.size
                        results[platform.id] = products
                            .filter { it.percent >= minDiscount }
                            .sortedByDescending { it.percent }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors[platform.id] = e.message ?: e.javaClass.simpleName
                    }
                    _ui.update {
                        it.copy(
                            results = LinkedHashMap(results),
                            scanned = LinkedHashMap(scanned),
                            errors = LinkedHashMap(errors),
                        )
                    }
                    if (index < platforms.lastIndex) delay(POLITE_DELAY_MS)
                }
            } finally {
                withContext(NonCancellable) { finishRun(query, minDiscount, results, scanned, errors) }
            }
        }
    }

    fun stop() {
        userGate?.complete(false)
        job?.cancel()
    }

    fun continueAfterUserAction() { userGate?.complete(true) }
    fun skipPlatform() { userGate?.complete(false) }

    private suspend fun waitForUser(platform: Platform, reason: Interruption): Boolean {
        val gate = CompletableDeferred<Boolean>()
        userGate = gate
        val instruction = when (reason) {
            Interruption.Verification -> "${platform.displayName} is asking for verification. Complete it in the page, then tap Continue."
            Interruption.Location -> "${platform.displayName} needs a delivery location. Set it in the page, then tap Continue."
        }
        _ui.update { it.copy(waitingForUser = true, status = instruction) }
        val proceed = withTimeoutOrNull(USER_WAIT_MS) { gate.await() } ?: false
        userGate = null
        _ui.update { it.copy(waitingForUser = false) }
        if (proceed) delay(1_500)
        return proceed
    }

    private suspend fun finishRun(
        query: String,
        minDiscount: Int,
        results: Map<String, List<Deal>>,
        scanned: Map<String, Int>,
        errors: Map<String, String>,
    ) {
        val now = System.currentTimeMillis()
        val run = Run(now, query, now, minDiscount, LinkedHashMap(scanned), LinkedHashMap(results))
        val history = if (run.total > 0) withContext(Dispatchers.IO) { store.save(run) } else _ui.value.history
        _ui.update {
            it.copy(
                running = false, waitingForUser = false, tab = Tab.Results,
                status = "", results = LinkedHashMap(results), scanned = LinkedHashMap(scanned),
                errors = LinkedHashMap(errors), history = history, progressDone = it.progressTotal,
            )
        }
    }

    // --- Results & history -------------------------------------------------------------------

    fun currentRun(): Run {
        val s = _ui.value
        return Run(0, s.resultsQuery, System.currentTimeMillis(), s.resultsMinDiscount, s.scanned, s.results)
    }

    fun openRun(run: Run) = _ui.update {
        it.copy(
            resultsQuery = run.query, resultsMinDiscount = run.minDiscount, results = run.results,
            scanned = run.scanned, errors = emptyMap(), tab = Tab.Results,
        )
    }

    fun deleteRun(run: Run) {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { store.delete(run.id) }
            _ui.update { it.copy(history = history) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clear() }
            _ui.update { it.copy(history = emptyList()) }
        }
    }

    fun exportCsv(resolver: ContentResolver, uri: Uri) {
        val csv = CsvExporter.build(currentRun())
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } != null
                } catch (_: Exception) {
                    false
                }
            }
            _messages.trySend(if (ok) "CSV saved" else "Could not save the file")
        }
    }

    fun toast(message: String) { _messages.trySend(message) }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLITE_DELAY_MS = 2_500L   // pause between sites so we behave like a person browsing
        const val USER_WAIT_MS = 5 * 60_000L
    }
}
