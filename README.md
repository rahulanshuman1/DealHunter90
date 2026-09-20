# LinkHarvest

Android app (Kotlin, Jetpack Compose) that searches **Amazon.in, Flipkart, Myntra, Ajio, Swiggy Instamart,
Zepto, Blinkit and BigBasket** for a keyword and collects the product-page URLs of items that are
**90% off or more** (adjustable from 50% to 95%). Results can be copied, shared, or exported to CSV.

## How the discount is determined

For every product link on a results page, the app finds the product's card and reads its text:

1. An explicit percentage the site prints ("90% off", "-90%", "Save 90%") is used as is. Banner wording that is
   not a firm product discount ("Up to 90% off", "Min. 50% off", "Extra 10% off", "10% off with bank card") is ignored.
2. If no percentage is printed but the card shows exactly two rupee amounts (price and MRP), the percentage is
   calculated and labelled **calculated** in the app and in the CSV.

Anything else is treated as "no discount found". The parser is in `data/Discount.kt` with tests in
`DiscountParserTest.kt`. Deep discounts are often listed against inflated MRPs, so always confirm on the product page.

## How it works

A real WebView is driven the way a person browses: open the site's search page, scroll to load more
results, read the product links and their card text, and keep only those that match the site's product-page URL pattern
(`data/Platform.kt`). Using the site's own rendered page means no private APIs are reverse-engineered
and JavaScript-heavy sites work.

* **Delivery location** – Zepto, Blinkit, Instamart and BigBasket show products only after a location is
  set. Open the site from the **Browser** tab once; cookies persist. If a run finds nothing on one of these,
  the app pauses and asks you to set a location, then continues.
* **CAPTCHA / verification** – the app does not try to bypass these. It pauses and you solve it in the page.
* **Scope** – you get the products a search page shows (plus what infinite-scroll loads), not a full catalogue
  crawl. Raise *Scroll depth* for more.

## Build the APK

**Option A – Android Studio:** open this folder, let Gradle sync, then *Build > Build APK(s)*
(or `./gradlew assembleRelease` -> `app/build/outputs/apk/release/`). Requires JDK 17 and Android SDK 35.

**Option B – GitHub Actions (no local setup):** push this folder to a GitHub repo. The workflow in
`.github/workflows/build-apk.yml` runs the unit tests, builds the APK and attaches it as a downloadable
artifact (Actions tab -> latest run -> *LinkHarvest-apk*).

### Signing

Without a keystore the release APK is signed with the debug key, which is fine for installing on your own
devices. For Google Play, create a keystore and supply `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` as environment variables (in CI: repository secrets, with the keystore base64-encoded in
`KEYSTORE_BASE64`), and build an AAB with `./gradlew bundleRelease`. Play also requires a current
`targetSdk`; bump `compileSdk`/`targetSdk` in `app/build.gradle.kts` (and AGP if needed) when it moves.

## When a site stops returning links

Retailers change URL formats and page structure. All per-site rules are in
`app/src/main/kotlin/app/linkharvest/data/Platform.kt` (search URL + product-URL regex) and covered by
`PlatformTest.kt`. Update the regex, add a test with a real product URL, rebuild.

## Responsible use

Automated collection may be restricted by a retailer's terms of use or robots rules. This app is paced
(pauses between sites and scrolls) and runs on your own device at human-like volume, but you are
responsible for complying with each site's terms. For commercial or large-scale use, use official
programs instead (Amazon Creators/PA-API, Flipkart Affiliate API, etc.).
