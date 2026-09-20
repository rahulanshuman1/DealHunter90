package app.linkharvest.data

/** A product link plus the discount found on its result card. */
data class Deal(
    val url: String,
    val percent: Int,
    /** Percentage calculated from price and MRP rather than printed by the site. */
    val computed: Boolean,
    /** Short piece of the card text (price, MRP, badge) so the result can be sanity-checked. */
    val snippet: String,
)

/** One completed (or stopped) collection. */
data class Run(
    val id: Long,
    val query: String,
    val timestampMillis: Long,
    val minDiscount: Int,
    /** platformId -> how many distinct products were looked at. */
    val scanned: Map<String, Int>,
    /** platformId -> products at or above [minDiscount], best discount first. */
    val results: Map<String, List<Deal>>,
) {
    val total: Int get() = results.values.sumOf { it.size }
}

object CsvExporter {
    fun build(run: Run): String = buildString {
        append("platform,query,discount_percent,discount_source,url,details\r\n")
        for ((platformId, deals) in run.results) {
            val name = Platforms.byId[platformId]?.displayName ?: platformId
            for (deal in deals) {
                append(escape(name)).append(',')
                append(escape(run.query)).append(',')
                append(deal.percent).append(',')
                append(if (deal.computed) "calculated" else "shown by site").append(',')
                append(escape(deal.url)).append(',')
                append(escape(deal.snippet)).append("\r\n")
            }
        }
    }

    fun plainText(run: Run): String = run.results.values.flatten().joinToString("\n") { it.url }

    private fun escape(value: String): String {
        // Neutralise spreadsheet formula injection, then apply RFC 4180 quoting.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }
}
