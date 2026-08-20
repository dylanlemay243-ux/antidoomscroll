package com.taper.app

/**
 * The only file you edit to go from Google's test ads to your own earning ads.
 *
 * Both values come from your AdMob account (apps.admob.com), and they are different
 * things that are easy to mix up:
 *
 *   APP_ID   ca-app-pub-0000000000000000~1111111111   one per app, has a TILDE
 *   NATIVE_UNIT_ID  ca-app-pub-0000000000000000/2222222222   one per ad slot, has a SLASH
 *
 * Steps:
 *  1. Create an AdMob account, then Apps → Add app → Android → "No, it isn't listed
 *     on a store yet" (you don't need to be published to get IDs).
 *  2. Copy the App ID it gives you into APP_ID below AND into AndroidManifest.xml,
 *     where the com.google.android.gms.ads.APPLICATION_ID meta-data lives. Both have
 *     to match or the app crashes on launch.
 *  3. Ad units → Add ad unit → Native. Name it "Pause screen". Copy that ID into
 *     NATIVE_UNIT_ID.
 *  4. Set USE_TEST to false, rebuild, install.
 *
 * Leave USE_TEST true while developing. Tapping your own live ads is what gets AdMob
 * accounts suspended, and test ads always fill so you can actually see the layout.
 */
object AdConfig {

    /**
     * Test ads while you're building and beta-testing: they always fill, so you can
     * actually see the panel, and they're safe to tap. Flip to false for release — a
     * brand-new AdMob unit often serves nothing for days, which looks like a bug but
     * isn't.
     */
    const val USE_TEST = true

    const val APP_ID = "ca-app-pub-3247097315955375~6001209208"
    const val NATIVE_UNIT_ID = "ca-app-pub-3247097315955375/1436791009"

    // Google's public test IDs. Always fill, never earn, safe to tap.
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_NATIVE_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    val nativeUnitId: String
        get() = if (USE_TEST) TEST_NATIVE_UNIT_ID else NATIVE_UNIT_ID

    /**
     * Your own phones. Devices listed here always get test ads, even with the live IDs
     * above — which is the protection against accidentally tapping your own live ads
     * and getting the AdMob account suspended.
     *
     * To find yours: install, open the app, and search logcat for "Use
     * RequestConfiguration.Builder().setTestDeviceIds". Android prints the hash for
     * the device. Paste it in below and rebuild.
     */
    val TEST_DEVICE_IDS: List<String> = listOf(
        // "33BE2250B43518CCDA7DE426D04EE231",
    )

    /** True when the real IDs still look like the placeholders above. */
    val unconfigured: Boolean
        get() = !USE_TEST && (APP_ID.startsWith("ca-app-pub-0000") ||
            NATIVE_UNIT_ID.startsWith("ca-app-pub-0000"))
}
