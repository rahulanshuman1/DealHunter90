package app.linkharvest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTest {
    private fun p(id: String) = Platforms.byId.getValue(id)

    @Test fun amazonNormalizesToCanonicalDpUrl() {
        val expected = "https://www.amazon.in/dp/B0CX59H5W7"
        assertEquals(expected, p("amazon").normalize("https://www.amazon.in/Some-Product-Name/dp/B0CX59H5W7/ref=sr_1_1?crid=1&keywords=x"))
        assertEquals(expected, p("amazon").normalize("https://www.amazon.in/gp/product/B0CX59H5W7?psc=1"))
        assertEquals(expected, p("amazon").normalize("https://www.amazon.in/dp/B0CX59H5W7"))
    }

    @Test fun amazonRejectsOtherPagesAndHosts() {
        assertNull(p("amazon").normalize("https://www.amazon.in/s?k=phone"))
        assertNull(p("amazon").normalize("https://www.amazon.in/gp/cart/view.html"))
        assertNull(p("amazon").normalize("https://evil.example.com/dp/B0CX59H5W7"))
        assertNull(p("amazon").normalize("https://notamazon.in/dp/B0CX59H5W7"))
    }

    @Test fun flipkartKeepsOnlyPid() {
        assertEquals(
            "https://www.flipkart.com/acme-phone-128-gb/p/itm1234abcd5678?pid=MOBGX12345",
            p("flipkart").normalize("https://www.flipkart.com/acme-phone-128-gb/p/itm1234abcd5678?pid=MOBGX12345&lid=LSTX&marketplace=FLIPKART&q=phone"),
        )
        assertEquals(
            "https://www.flipkart.com/acme-phone/p/itm1234abcd5678",
            p("flipkart").normalize("https://www.flipkart.com/acme-phone/p/itm1234abcd5678?lid=abc"),
        )
        assertNull(p("flipkart").normalize("https://www.flipkart.com/mobiles/pr?sid=tyy"))
    }

    @Test fun myntraProductEndsWithIdAndBuy() {
        assertEquals(
            "https://www.myntra.com/tshirts/roadster/roadster-men-tshirt/1234567/buy",
            p("myntra").normalize("https://www.myntra.com/tshirts/roadster/roadster-men-tshirt/1234567/buy?x=1"),
        )
        assertNull(p("myntra").normalize("https://www.myntra.com/tshirts"))
    }

    @Test fun ajioProduct() {
        assertEquals(
            "https://www.ajio.com/acme-slim-fit-shirt/p/469012345_blue",
            p("ajio").normalize("https://www.ajio.com/acme-slim-fit-shirt/p/469012345_blue?foo=bar"),
        )
        assertNull(p("ajio").normalize("https://www.ajio.com/men-shirts/c/830216013"))
    }

    @Test fun blinkitProduct() {
        assertEquals(
            "https://blinkit.com/prn/amul-taaza-milk/prid/12345",
            p("blinkit").normalize("https://blinkit.com/prn/amul-taaza-milk/prid/12345"),
        )
        assertNull(p("blinkit").normalize("https://blinkit.com/cn/dairy/cid/14/922"))
    }

    @Test fun bigBasketProductAndTrailingSlash() {
        assertEquals(
            "https://www.bigbasket.com/pd/10000148/fresho-onion-1-kg",
            p("bigbasket").normalize("https://www.bigbasket.com/pd/10000148/fresho-onion-1-kg/"),
        )
        assertNull(p("bigbasket").normalize("https://www.bigbasket.com/cl/fruits-vegetables/"))
    }

    @Test fun zeptoAndInstamart() {
        assertEquals(
            "https://www.zepto.com/pn/amul-milk/pvid/0a1b2c3d-1111-2222-3333-444455556666",
            p("zepto").normalize("https://www.zepto.com/pn/amul-milk/pvid/0a1b2c3d-1111-2222-3333-444455556666?utm=x"),
        )
        assertEquals(
            "https://www.swiggy.com/instamart/item/ABC123XYZ",
            p("instamart").normalize("https://www.swiggy.com/instamart/item/ABC123XYZ?storeId=1"),
        )
        assertNull(p("instamart").normalize("https://www.swiggy.com/restaurants/foo"))
    }

    @Test fun rejectsGarbageAndNonHttp() {
        for (platform in Platforms.all) {
            assertNull(platform.normalize("javascript:alert(1)"))
            assertNull(platform.normalize("not a url at all"))
            assertNull(platform.normalize(""))
            assertNull(platform.normalize("intent://scan/#Intent;scheme=zxing;end"))
        }
    }

    @Test fun searchUrlEncodesQuery() {
        assertEquals("https://www.amazon.in/s?k=iphone+15+%26+case", p("amazon").searchUrl("  iphone 15 & case "))
        assertEquals("https://www.myntra.com/white-t-shirt?rawQuery=White+T-Shirt", p("myntra").searchUrl("White T-Shirt"))
    }

    @Test fun allEightPlatformsPresentWithUniqueIds() {
        assertEquals(8, Platforms.all.size)
        assertEquals(8, Platforms.all.map { it.id }.toSet().size)
    }

    @Test fun csvEscapesAndNeutralisesFormulas() {
        val deal = Deal("https://www.amazon.in/dp/B0CX59H5W7", 90, false, "₹199, M.R.P ₹1,999")
        val run = Run(1, "=cmd, \"x\"", 0, 90, mapOf("amazon" to 5), mapOf("amazon" to listOf(deal)))
        val csv = CsvExporter.build(run)
        assertTrue(csv.startsWith("platform,query,discount_percent,discount_source,url,details\r\n"))
        assertTrue(csv.contains("Amazon,\"'=cmd, \"\"x\"\"\",90,shown by site,https://www.amazon.in/dp/B0CX59H5W7,\"₹199, M.R.P ₹1,999\""))
        assertEquals("https://www.amazon.in/dp/B0CX59H5W7", CsvExporter.plainText(run))
    }
}
