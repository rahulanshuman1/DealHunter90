package app.linkharvest.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val MAX_TEXT_CHARS = 200_000

@Composable
fun LinkHarvestApp(webView: WebView, vm: MainViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? -> if (uri != null) vm.exportCsv(context.contentResolver, uri) }

    BackHandler(enabled = state.tab != Tab.Search) {
        if (state.tab == Tab.Browser && !state.running && vm.canGoBack()) vm.goBack()
        else vm.selectTab(Tab.Search)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.Search -> Icons.Default.Search
                                    Tab.Browser -> Icons.Default.Home
                                    Tab.Results -> Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(if (tab == Tab.Results && state.totalLinks > 0) "Results (${state.totalLinks})" else tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // The WebView stays attached at full size on every tab so JavaScript keeps running
            // during a collection; the other tabs simply draw an opaque surface over it.
            AndroidView(
                factory = {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                modifier = Modifier.fillMaxSize(),
            )
            when (state.tab) {
                Tab.Search -> Surface(Modifier.fillMaxSize()) {
                    SearchScreen(
                        state = state,
                        onQuery = vm::setQuery,
                        onTogglePlatform = vm::togglePlatform,
                        onSetAll = vm::setAllPlatforms,
                        onMaxScrolls = vm::setMaxScrolls,
                        onMinDiscount = vm::setMinDiscount,
                        onStart = vm::start,
                        onOpenRun = vm::openRun,
                        onDeleteRun = vm::deleteRun,
                        onClearHistory = vm::clearHistory,
                    )
                }
                Tab.Results -> Surface(Modifier.fillMaxSize()) {
                    ResultsScreen(
                        state = state,
                        onOpenLink = { openLink(context, it, vm) },
                        onCopyLink = { copy(context, it, vm) },
                        onCopyAll = {
                            val text = allLinksText(vm)
                            if (text.length > MAX_TEXT_CHARS) vm.toast("Too many links to copy — use Export CSV")
                            else copy(context, text, vm)
                        },
                        onShare = {
                            val text = allLinksText(vm)
                            if (text.length > MAX_TEXT_CHARS) vm.toast("Too many links to share as text — use Export CSV")
                            else share(context, text)
                        },
                        onExport = { exportLauncher.launch("links-${safeName(state.resultsQuery)}.csv") },
                    )
                }
                Tab.Browser -> BrowserPanel(
                    state = state,
                    onContinue = vm::continueAfterUserAction,
                    onSkip = vm::skipPlatform,
                    onStop = vm::stop,
                    onOpenSite = vm::openSite,
                )
            }
        }
    }
}

private fun allLinksText(vm: MainViewModel): String =
    app.linkharvest.data.CsvExporter.plainText(vm.currentRun())

private fun safeName(query: String): String =
    query.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "export" }

private fun openLink(context: Context, url: String, vm: MainViewModel) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        vm.toast("No app can open this link")
    }
}

private fun copy(context: Context, text: String, vm: MainViewModel) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("links", text))
    vm.toast("Copied")
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share links").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
