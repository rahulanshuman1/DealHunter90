package app.linkharvest.data

data class DiscountInfo(
    val percent: Int,
    /** True when the percentage was calculated from two prices rather than read from the page. */
    val computed: Boolean,
)

/**
 * Reads a product's discount from the text of its result card.
 *
 * Order of trust:
 *  1. An explicit percentage the site prints ("90% off", "(90% OFF)", "-90%", "Save 90%").
 *     Banner-style wording that is not a firm product discount ("Up to 60% off", "Min. 40% off",
 *     "Extra 10% off", "10% off with bank card") is ignored.
 *  2. Otherwise, if the card shows exactly two distinct rupee amounts (selling price and MRP),
 *     the percentage is calculated from them and flagged as [DiscountInfo.computed].
 *
 * Pure Kotlin, no Android dependencies, so it is covered by plain JVM unit tests.
 */
object DiscountParser {
    private val explicit = listOf(
        Regex("""(\d{1,2})\s*%\s*(?:off|discount)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:save|saving|discount)\s*(?:of\s*)?(\d{1,2})\s*%""", RegexOption.IGNORE_CASE),
        Regex("""(?<![\w.])[-\u2212\u2013]\s*(\d{1,2})\s*%"""),
    )

    private val bannerBefore = Regex(
        """(?:up\s*to|upto|min\.?|minimum|starting(?:\s+at)?|from|extra|additional)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val conditionalAfter = Regex(
        """^\W{0,3}(?:(?:with|using|via|on)\s+)?(?:your\s+)?(?:first|coupon|banks?|cards?|cashback|prepaid|upi|credit|debit)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val rupeeAmount = Regex("""(?:\u20B9|rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): DiscountInfo? {
        var best = 0
        for (pattern in explicit) {
            for (match in pattern.findAll(text)) {
                val percent = match.groupValues[1].toIntOrNull() ?: continue
                if (percent !in 1..99) continue
                val before = text.substring(maxOf(0, match.range.first - 16), match.range.first)
                val after = text.substring(match.range.last + 1, minOf(text.length, match.range.last + 1 + 30))
                if (bannerBefore.containsMatchIn(before) || conditionalAfter.containsMatchIn(after)) continue
                if (percent > best) best = percent
            }
        }
        if (best > 0) return DiscountInfo(best, computed = false)

        val amounts = rupeeAmount.findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
            .filter { it > 0 }
            .toSet()
        if (amounts.size == 2) {
            val high = amounts.max()
            val low = amounts.min()
            val percent = ((high - low) * 100 / high).toInt()
            if (percent in 1..99) return DiscountInfo(percent, computed = true)
        }
        return null
    }
}
