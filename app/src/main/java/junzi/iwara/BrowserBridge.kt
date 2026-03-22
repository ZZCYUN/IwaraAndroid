package junzi.iwara

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import junzi.iwara.model.IwaraSite
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BrowserBridge {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<BrowserResponse>>()

    @Volatile
    private var webView: WebView? = null
    @Volatile
    private var activeLoad: CompletableDeferred<Unit>? = null
    @Volatile
    private var bridgeReady = false
    @Volatile
    private var activeSite = IwaraSite.Tv

    fun isAttached(): Boolean = webView != null

    fun attach(candidate: WebView) {
        if (webView === candidate) return
        webView = candidate
        bridgeReady = false
        candidate.settings.javaScriptEnabled = true
        candidate.settings.domStorageEnabled = true
        candidate.settings.loadsImagesAutomatically = false
        candidate.settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(candidate, true)
        candidate.addJavascriptInterface(BridgeJsInterface, "IwaraBridge")
        candidate.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                probePageReady(view)
            }
        }
        beginLoad(IwaraSite.Tv.homeUrl, IwaraSite.Tv)
    }

    suspend fun fetch(
        site: IwaraSite,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): BrowserResponse {
        ensureReady(site)
        return executeFetch(site, method, url, headers, body, allowChallengeRetry = true)
    }

    private suspend fun executeFetch(
        site: IwaraSite,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        allowChallengeRetry: Boolean,
    ): BrowserResponse {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BrowserResponse>()
        pending[requestId] = deferred

        val headersJson = org.json.JSONObject(headers).toString()
        val bodyJson = body?.let(org.json.JSONObject::quote) ?: "null"
        val escapedUrl = org.json.JSONObject.quote(url)
        val escapedMethod = org.json.JSONObject.quote(method)
        val script = """
            (function() {
              const requestId = ${org.json.JSONObject.quote(requestId)};
              const options = { method: $escapedMethod, headers: $headersJson, credentials: 'include' };
              const body = $bodyJson;
              if (body !== null) options.body = body;
              fetch($escapedUrl, options)
                .then(async (response) => {
                  const text = await response.text();
                  IwaraBridge.postResult(requestId, JSON.stringify({ status: response.status, body: text }));
                })
                .catch((error) => {
                  IwaraBridge.postResult(requestId, JSON.stringify({ status: 0, body: String(error) }));
                });
            })();
        """.trimIndent()

        mainHandler.post { webView?.evaluateJavascript(script, null) }
        val response = deferred.await()
        if (allowChallengeRetry && response.isCloudflareChallenge()) {
            resolveChallenge(site, url)
            return executeFetch(site, method, url, headers, body, allowChallengeRetry = false)
        }
        return response
    }

    private suspend fun ensureReady(site: IwaraSite) {
        if (bridgeReady && activeSite == site) return
        beginLoad(site.homeUrl, site)
        activeLoad?.await()
    }

    private suspend fun resolveChallenge(site: IwaraSite, url: String) {
        beginLoad(url, site)
        activeLoad?.await()
        beginLoad(site.homeUrl, site)
        activeLoad?.await()
    }

    private fun beginLoad(url: String, site: IwaraSite) {
        val deferred = CompletableDeferred<Unit>()
        activeLoad = deferred
        bridgeReady = false
        activeSite = site
        mainHandler.post {
            webView?.loadUrl(url)
        }
    }

    private fun probePageReady(view: WebView) {
        view.evaluateJavascript(
            "(function(){return JSON.stringify({title:document.title, body: (document.body && document.body.innerText ? document.body.innerText.slice(0,400) : '')});})()",
        ) { raw ->
            val payload = raw?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\\"", "\"")?.replace("\\n", "\n")
            val text = payload ?: ""
            val waiting = text.contains("Just a moment", ignoreCase = true) ||
                text.contains("Enable JavaScript and cookies to continue", ignoreCase = true)
            if (waiting) {
                mainHandler.postDelayed({ probePageReady(view) }, 2000)
            } else {
                bridgeReady = true
                activeLoad?.complete(Unit)
                activeLoad = null
            }
        }
    }

    private object BridgeJsInterface {
        @JavascriptInterface
        fun postResult(requestId: String, payload: String) {
            val result = runCatching {
                val json = org.json.JSONObject(payload)
                BrowserResponse(
                    statusCode = json.optInt("status"),
                    body = json.optString("body"),
                )
            }.getOrElse {
                BrowserResponse(0, payload)
            }
            pending.remove(requestId)?.complete(result)
        }
    }
}

data class BrowserResponse(
    val statusCode: Int,
    val body: String,
) {
    fun isCloudflareChallenge(): Boolean {
        return body.contains("Just a moment", ignoreCase = true) ||
            body.contains("__cf_chl_opt", ignoreCase = true) ||
            body.contains("Enable JavaScript and cookies to continue", ignoreCase = true)
    }
}


