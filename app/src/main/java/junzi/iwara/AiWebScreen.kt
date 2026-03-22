package junzi.iwara

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import junzi.iwara.app.IwaraAppController
import junzi.iwara.model.AppRoute
import junzi.iwara.model.AppUiState

private const val AI_HOME_URL = "https://www.iwara.ai/"
private const val AI_DOMAIN = "www.iwara.ai"

@Composable
fun AiSessionSyncHost(refreshToken: String?) {
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            webChromeClient = WebChromeClient()
        }
    }

    fun syncHiddenToken() {
        if ((webView.url ?: AI_HOME_URL).contains(AI_DOMAIN).not()) return
        val tokenLiteral = refreshToken?.let(::quoteJsString) ?: "null"
        val script = """
            (function() {
              var desired = $tokenLiteral;
              var current = localStorage.getItem('token');
              if (desired === null) {
                if (current !== null) {
                  localStorage.removeItem('token');
                  return 'reload';
                }
                return 'ok';
              }
              if (current !== desired) {
                localStorage.setItem('token', desired);
                return 'reload';
              }
              return 'ok';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (raw?.contains("reload") == true) {
                webView.reload()
            }
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if ((url ?: AI_HOME_URL).contains(AI_DOMAIN)) {
                    syncHiddenToken()
                }
            }
        }
        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(AI_HOME_URL)
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(refreshToken) {
        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(AI_HOME_URL)
        } else if ((webView.url ?: AI_HOME_URL).contains(AI_DOMAIN)) {
            syncHiddenToken()
        } else {
            webView.loadUrl(AI_HOME_URL)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier
            .padding(0.dp)
            .height(1.dp)
            .fillMaxSize(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWebScreen(
    state: AppUiState,
    controller: IwaraAppController,
) {
    val context = LocalContext.current
    var canGoBack by remember { mutableStateOf(false) }
    var pendingSyncToken by remember(state.session?.refreshToken) { mutableStateOf(true) }
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
        }
    }

    fun syncToken() {
        if ((webView.url ?: AI_HOME_URL).contains(AI_DOMAIN).not()) return
        val tokenLiteral = state.session?.refreshToken?.let(::quoteJsString) ?: "null"
        val script = """
            (function() {
              var desired = $tokenLiteral;
              var current = localStorage.getItem('token');
              if (desired === null) {
                if (current !== null) {
                  localStorage.removeItem('token');
                  return 'reload';
                }
                return 'ok';
              }
              if (current !== desired) {
                localStorage.setItem('token', desired);
                return 'reload';
              }
              return 'ok';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            pendingSyncToken = false
            if (raw?.contains("reload") == true) {
                webView.reload()
            }
        }
    }

    DisposableEffect(webView) {
        val client = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                canGoBack = view.canGoBack()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                canGoBack = view.canGoBack()
                if (pendingSyncToken && (url ?: AI_HOME_URL).contains(AI_DOMAIN)) {
                    syncToken()
                }
            }
        }
        webView.webViewClient = client
        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(AI_HOME_URL)
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(state.session?.refreshToken) {
        pendingSyncToken = true
        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(AI_HOME_URL)
        } else if ((webView.url ?: AI_HOME_URL).contains(AI_DOMAIN)) {
            syncToken()
        } else {
            webView.loadUrl(AI_HOME_URL)
        }
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_ai)) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { webView.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = controller::logout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.action_logout))
                    }
                },
            )
        },
        bottomBar = {
            MainBottomBar(
                route = AppRoute.Ai,
                isOwnProfile = false,
                onOpenHome = controller::openFeed,
                onOpenAi = controller::openAi,
                onOpenMy = { controller.openOwnProfile() },
            )
        },
    ) { paddingValues ->
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            update = { view ->
                canGoBack = view.canGoBack()
                if (view.url.isNullOrBlank()) {
                    view.loadUrl(AI_HOME_URL)
                }
            },
        )
    }
}

private fun quoteJsString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}



