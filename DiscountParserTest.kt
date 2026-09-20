package app.linkharvest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscountParserTest {
    private fun pct(text: String) = DiscountParser.parse(text)?.percent
    private fun computed(text: String) = DiscountParser.parse(text)?.computed

    @Test fun amazonStyle() {
        assertEquals(90, pct("Acme Wireless Earbuds\n4.1\n₹199\nM.R.P: ₹1,999\n(90% off)\nFREE Delivery"))
    }

    @Test fun flipkartAndAjioStyle() {
        assertEquals(91, pct("Acme Shoes ₹179 ₹1,999 91% off"))
        assertEquals(92, pct("ACME Men Shirt\nRs. 159\nRs. 1999\n92% off"))
    }

    @Test fun myntraStyle() {
        assertEquals(90, pct("Roadster\nMen T-shirt\nRs. 149 Rs. 1499 (90% OFF)"))
    }

    @Test fun badgeStylesWithDash() {
        assertEquals(90, pct("Fresh Milk ₹5 ₹50 -90%"))
        assertEquals(90, pct("Save 90% today"))
    }

    @Test fun ignoresBannerAndConditionalDiscounts() {
        assertNull(pct("Up to 90% off on fashion"))
        assertNull(pct("UPTO 90% OFF"))
        assertNull(pct("Min. 90% off"))
        assertNull(pct("Extra 90% off"))
        assertNull(pct("Flat 90% off with bank card"))
        assertNull(pct("90% off on your first order"))
    }

    @Test fun notFooledByWordsThatMerelyStartLikeCondition() {
        assertEquals(90, pct("90% off\nCardigan for women"))
    }

    @Test fun takesLargestValidPercentage() {
        assertEquals(90, pct("Extra 10% off with coupon, 90% off"))
    }

    @Test fun calculatesFromTwoPricesWhenNoPercentShown() {
        assertEquals(90, pct("Acme Bottle ₹199 ₹1,999"))
        assertEquals(true, computed("Acme Bottle ₹199 ₹1,999"))
        assertEquals(false, computed("₹199 ₹1,999 (90% off)"))
    }

    @Test fun doesNotGuessFromOneOrThreePrices() {
        assertNull(pct("Acme Bottle ₹199"))
        assertNull(pct("₹100 ₹500 ₹40 delivery"))
    }

    @Test fun rejectsNonsense() {
        assertNull(pct(""))
        assertNull(pct("100% cotton"))
        assertNull(pct("0% off"))
        assertNull(pct("Rated 4.5 out of 5"))
    }
}
