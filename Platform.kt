package app.linkharvest.data

import java.net.URI
import java.net.URLEncoder

/**
 * Describes one shopping site: where to search, and what a *product page* URL looks like.
 *
 * All site-specific knowledge lives in [Platforms]. Retailers change their URL formats from time to
 * time; when a platform stops returning links, this is the only place that should need updating.
 *
 * This file is deliberately free of Android APIs so it can be unit-tested on a plain JVM.
 */
data class Platform(
    val id: String,
    val displayName: String,
    val homeUrl: String,
    /** Search URL template. `{q}` = URL-encoded query, `{slug}` = dash-separated lowercase query. */
    val searchTemplate: String,
    /** Registrable domains a product link may belong to (subdomains are accepted). */
    val hostSuffixes: List<String>,
    /** Matched against the URL path. Capture group 1 is the product id where one exists. */
    val productPath: Regex,
    /** Query-string parameters worth keeping on the normalized URL (everything else is dropped). */
    val keepParams: Set<String> = emptySet(),
    /** If set, the normalized URL is built from this template, `{1}` being capture group 1. */
    val canonicalTemplate: String? = null,
    /** Quick-commerce apps show results only after a delivery location is chosen. */
    val needsLocation: Boolean = false,
) {
    fun searchUrl(query: String): String {
        val q = query.trim()
        return searchTemplate
            .replace("{slug}", slugify(q))
            .replace("{q}", URLEncoder.encode(q, "UTF-8"))
    }

    /**
     * Returns a clean, de-duplicable product URL, or null if [raw] is not a product page of this
     * platform (wrong host, category page, tracking redirect, etc.).
     */
    fun normalize(raw: String): String? {
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (hostSuffixes.none { host == it || host.endsWith(".$it") }) return null

        val path = uri.rawPath ?: return null
        val match = productPath.find(path) ?: return null

        canonicalTemplate?.let { template ->
            val id = match.groupValues.getOrNull(1).orEmpty()
            return template.replace("{1}", id)
        }

        val base = "https://$host${path.trimEnd('/')}"
        val kept = uri.rawQuery
            ?.split('&')
            ?.filter { part -> part.substringBefore('=') in keepParams }
            .orEmpty()
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private fun slugify(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

object Platforms {
    val all: List<Platform> = listOf(
        Platform(
            id = "amazon",
            displayName = "Amazon",
            homeUrl = "https://www.amazon.in/",
            searchTemplate = "https://www.amazon.in/s?k={q}",
            hostSuffixes = listOf("amazon.in"),
            productPath = Regex("""/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})(?:/|$)"""),
            canonicalTemplate = "https://www.amazon.in/dp/{1}",
        ),
        Platform(
            id = "flipkart",
            displayName = "Flipkart",
            homeUrl = "https://www.flipkart.com/",
            searchTemplate = "https://www.flipkart.com/search?q={q}",
            hostSuffixes = listOf("flipkart.com"),
            productPath = Regex("""/p/(itm[a-zA-Z0-9]+)/?$"""),
            keepParams = setOf("pid"),
        ),
        Platform(
            id = "myntra",
            displayName = "Myntra",
            homeUrl = "https://www.myntra.com/",
            searchTemplate = "https://www.myntra.com/{slug}?rawQuery={q}",
            hostSuffixes = listOf("myntra.com"),
            productPath = Regex("""^/(?:[^/]+/)+(\d{4,})/buy/?$"""),
        ),
        Platform(
            id = "ajio",
            displayName = "Ajio",
            homeUrl = "https://www.ajio.com/",
            searchTemplate = "https://www.ajio.com/search/?text={q}",
            hostSuffixes = listOf("ajio.com"),
            productPath = Regex("""/p/(\d{6,}(?:_[A-Za-z0-9]+)?)/?$"""),
        ),
        Platform(
            id = "instamart",
            displayName = "Swiggy Instamart",
            homeUrl = "https://www.swiggy.com/instamart",
            searchTemplate = "https://www.swiggy.com/instamart/search?custom_back=true&query={q}",
            hostSuffixes = listOf("swiggy.com"),
            productPath = Regex("""/(?:stores/)?instamart/item/([A-Za-z0-9]+)/?$"""),
            needsLocation = true,
        ),
        Platform(
            id = "zepto",
            displayName = "Zepto",
            homeUrl = "https://www.zepto.com/",
            searchTemplate = "https://www.zepto.com/search?query={q}",
            hostSuffixes = listOf("zepto.com", "zeptonow.com"),
            productPath = Regex("""/pn/[^/]+/pvid/([A-Za-z0-9-]+)/?$"""),
            needsLocation = true,
        ),
        Platform(
            id = "blinkit",
            displayName = "Blinkit",
            homeUrl = "https://blinkit.com/",
            searchTemplate = "https://blinkit.com/s/?q={q}",
            hostSuffixes = listOf("blinkit.com"),
            productPath = Regex("""/prn/[^/]+/prid/(\d+)/?$"""),
            needsLocation = true,
        ),
        Platform(
            id = "bigbasket",
            displayName = "BigBasket",
            homeUrl = "https://www.bigbasket.com/",
            searchTemplate = "https://www.bigbasket.com/ps/?q={q}",
            hostSuffixes = listOf("bigbasket.com"),
            productPath = Regex("""/pd/(\d+)(?:/[^/]*)?/?$"""),
            needsLocation = true,
        ),
    )

    val byId: Map<String, Platform> = all.associateBy { it.id }
}
