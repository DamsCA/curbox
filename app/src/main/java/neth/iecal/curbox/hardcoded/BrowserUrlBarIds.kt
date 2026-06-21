package neth.iecal.curbox.hardcoded

import neth.iecal.curbox.data.models.BrowserUrlBarInfo


val URL_BAR_ID_LIST = mapOf(
    "com.android.chrome" to BrowserUrlBarInfo(
        displayUrlBarId = "com.android.chrome:id/url_bar",
    ),
    "org.cromite.cromite" to BrowserUrlBarInfo(
        displayUrlBarId = "org.cromite.cromite:id/url_bar",
    ),
    "app.vanadium.browser" to BrowserUrlBarInfo(
        displayUrlBarId = "app.vanadium.browser:id/url_bar",
    ),
    "com.brave.browser" to BrowserUrlBarInfo(
        displayUrlBarId = "com.brave.browser:id/url_bar",
    ),


    "org.mozilla.firefox" to BrowserUrlBarInfo(
        displayUrlBarId = "ADDRESSBAR_URL_BOX",
    ),
    "org.mozilla.fennec_fdroid" to BrowserUrlBarInfo(
        displayUrlBarId = "ADDRESSBAR_URL_BOX",
    ),


    "com.opera.browser" to BrowserUrlBarInfo(
        displayUrlBarId = "com.opera.browser:id/url_field",
    ),

    "com.microsoft.emmx" to BrowserUrlBarInfo(
        displayUrlBarId = "com.microsoft.emmx:id/url_bar",
    ),
    "com.sec.android.app.sbrowser" to BrowserUrlBarInfo(
        displayUrlBarId = "com.sec.android.app.sbrowser:id/location_bar_edit_text",
    ),
    "com.microsoft.bing" to BrowserUrlBarInfo(
        displayUrlBarId = "com.microsoft.bing:id/url_bar",
    ),
)
