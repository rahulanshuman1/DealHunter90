package app.linkharvest.engine

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.linkharvest.data.Deal
import app.linkharvest.data.DiscountParser
import app.linkharvest.data.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import kotlin.coroutines.resume

/** Why the collector needs the person to act before it can continue. */
enum class Interruption { Verification, Location }

/**
 * Drives a real, visible [WebView] the way a person would: open the site's search page, scroll to
 * load more results, and read the product links out of the page.
 *
 * It does not spoof a browser identity or try to defeat bot checks. If a site shows a CAPTCHA or asks
 * for a delivery location, the collector pauses and hands control to the person.
 *
 * All methods must be called on the main thread (WebView requirement).
 */
class WebCollector(private val web: WebView) {

    private var pageLoaded: CompletableDeferred<Unit>? = null
    private var lastError: String? = null

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        with(web.settings) {
            javaScriptEnabled = true          // required: these sites render results with JavaScript
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep web pages inside the WebView; refuse app-launching schemes (intent://, market://).
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) lastError = error.description?.toString() ?: "Network error"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageLoaded?.complete(Unit)
            }
        }
    }

    /** Loads [url] and suspends until the page reports it finished (or the timeout passes). */
    private suspend fun load(url: String) {
        val done = CompletableDeferred<Unit>()
        pageLoaded = done
        lastError = null
        web.loadUrl(url)
        val finished = withTimeoutOrNull(PAGE_TIMEOUT_MS) { done.await() }
        pageLoaded = null
        lastError?.let { throw IOException("Could not load page: $it") }
        if (finished == null) throw IOException("Timed out loading the page. Check your connection.")
    }

    /**
     * Scans product cards for [query] on [platform] and returns every distinct product seen, each with
     * the best discount found on its card (0 when none was shown). The caller applies the threshold.
     *
     * @param onProgress called with (products scanned, products at or above [minDiscount]).
     * @param onNeedsUser called when the person must act in the page; return true to continue,
     *   false to skip this platform.
     */
    suspend fun collect(
        platform: Platform,
        query: String,
        maxScrolls: Int,
        minDiscount: Int,
        onProgress: (scanned: Int, matched: Int) -> Unit,
        onNeedsUser: suspend (Interruption) -> Boolean,
    ): List<Deal> {
        val searchUrl = platform.searchUrl(query)
        load(searchUrl)
        delay(INITIAL_RENDER_MS)

        if (isBlocked()) {
            if (!onNeedsUser(Interruption.Verification)) return emptyList()
        }

        val seen = LinkedHashMap<String, Deal>()
        fun merge(deals: List<Deal>) {
            for (deal in deals) {
                val old = seen[deal.url]
                if (old == null || deal.percent > old.percent) seen[deal.url] = deal
            }
        }
        fun report() = onProgress(seen.size, seen.values.count { it.percent >= minDiscount })

        merge(readProducts(platform))

        if (seen.isEmpty() && platform.needsLocation) {
            if (!onNeedsUser(Interruption.Location)) return emptyList()
            load(searchUrl) // reload so results reflect the chosen location
            delay(INITIAL_RENDER_MS)
            merge(readProducts(platform))
        }
        report()

        var stagnantRounds = 0
        for (round in 1..maxScrolls) {
            currentCoroutineContext().ensureActive()
            scrollDown()
            delay(if (seen.isEmpty()) SLOW_SCROLL_MS else SCROLL_MS)
            val before = seen.size
            merge(readProducts(platform))
            report()
            stagnantRounds = if (seen.size == before) stagnantRounds + 1 else 0
            if (stagnantRounds >= STAGNANT_LIMIT) break
        }
        return seen.values.toList()
    }

    /** Reads every product link on the page together with the text of the card that contains it. */
    private suspend fun readProducts(platform: Platform): List<Deal> {
        val json = decode(eval(productsScript(platform))) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val deals = ArrayList<Deal>(array.length())
            for (i in 0 until array.length()) {
                val pair = array.optJSONArray(i) ?: continue
                val url = platform.normalize(pair.optString(0)) ?: continue
                val text = pair.optString(1)
                val info = DiscountParser.parse(text)
                deals.add(
                    Deal(
                        url = url,
                        percent = info?.percent ?: 0,
                        computed = info?.computed ?: false,
                        snippet = text.replace(WHITESPACE, " ").trim().take(SNIPPET_LENGTH),
                    )
                )
            }
            deals
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Finds anchors whose path matches the platform's product pattern, then climbs from each one to
     * the smallest ancestor that still contains only that product: the "card" holding its price/badge.
     */
    private fun productsScript(platform: Platform): String {
        val pattern = JSONObject.quote(platform.productPath.pattern)
        return "(function(){var re=new RegExp($pattern),out=[],seen={};" +
            "var A=document.querySelectorAll('a[href]');" +
            "function isP(a){return re.test(a.pathname);}" +
            "function key(a){var m=re.exec(a.pathname);return (m&&m[1])?m[1]:a.origin+a.pathname;}" +
            "function card(a){var best=a,el=a;" +
            "for(var d=0;d<8&&el.parentElement;d++){var p=el.parentElement,L=p.querySelectorAll('a[href]'),set={},n=0;" +
            "for(var k=0;k<L.length;k++){if(isP(L[k])){var kk=key(L[k]);if(!set[kk]){set[kk]=1;n++;}}}" +
            "if(n>1)break;el=p;best=p;if((p.innerText||'').length>700)break;}" +
            "return (best.innerText||'').slice(0,700);}" +
            "for(var i=0;i<A.length;i++){var a=A[i];if(!isP(a)||seen[a.href])continue;seen[a.href]=1;out.push([a.href,card(a)]);}" +
            "return JSON.stringify(out);})()"
    }

    private suspend fun scrollDown() {
        eval(SCROLL_JS)
    }

    private suspend fun isBlocked(): Boolean {
        val text = decode(eval(PAGE_TEXT_JS)).orEmpty()
        return BLOCK_PATTERN.containsMatchIn(text)
    }

    private suspend fun eval(script: String): String? = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript(script) { result -> if (cont.isActive) cont.resume(result) }
    }

    /** evaluateJavascript returns a JSON-encoded value; unwrap a JSON string into a plain String. */
    private fun decode(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            JSONTokener(raw).nextValue() as? String
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val PAGE_TIMEOUT_MS = 35_000L
        const val INITIAL_RENDER_MS = 3_000L
        const val SCROLL_MS = 1_500L
        const val SLOW_SCROLL_MS = 3_000L
        const val STAGNANT_LIMIT = 2
        const val SNIPPET_LENGTH = 90
        val WHITESPACE = Regex("\\s+")

        val BLOCK_PATTERN = Regex(
            "captcha|robot check|are you a robot|access denied|unusual traffic|" +
                "verify you are (a )?human|enter the characters you see",
            RegexOption.IGNORE_CASE,
        )

        const val PAGE_TEXT_JS =
            "(function(){return (document.title||'')+' '+" +
                "((document.body&&document.body.innerText)||'').slice(0,1500);})()"

        // Scrolls the window and any inner scroll containers (quick-commerce sites scroll inside a div).
        const val SCROLL_JS =
            "(function(){window.scrollTo(0,document.documentElement.scrollHeight);" +
                "var els=document.querySelectorAll('div,main,section,ul');" +
                "for(var i=0;i<els.length;i++){var e=els[i];" +
                "if(e.scrollHeight>e.clientHeight+50){var s=getComputedStyle(e).overflowY;" +
                "if(s==='auto'||s==='scroll'){e.scrollTop=e.scrollHeight;}}}return 'ok';})()"
    }
}
