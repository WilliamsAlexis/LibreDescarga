package com.libredescarga.feature.browser.component

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.libredescarga.core.domain.browser.MediaSniffer
import com.libredescarga.core.domain.browser.NavigationDecision
import com.libredescarga.feature.browser.BrowserTab

/** Todo lo que el WebView le cuenta a la pantalla. */
internal interface WebViewCallbacks {
    /** Decisión inmediata para cada navegación (esquemas prohibidos y lista negra). */
    fun decide(url: String): NavigationDecision
    fun onBlocked(decision: NavigationDecision)
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String, title: String?)
    fun onUrlChanged(tabId: String, url: String)
    fun onProgress(tabId: String, progress: Int)
    fun onTitle(tabId: String, title: String)
    fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onError(tabId: String, error: Boolean)
    fun onMediaSniffed(tabId: String, url: String)
    fun onDomResult(tabId: String, raw: String?)
    fun onDrm(tabId: String)
    fun onDownload(url: String)

    /** El usuario mantuvo presionada una imagen de la página: [url] es la dirección de la imagen. */
    fun onImageLongPress(tabId: String, url: String)

    fun onShowCustomView(view: View, onHide: () -> Unit)
    fun onHideCustomView()
}

internal const val INCOGNITO_PROFILE = "libredescarga_incognito"

// Cuándo (en milisegundos) se vuelve a buscar medios en la página después de cargarla.
private val RESCAN_DELAYS_MS = listOf(2_000L, 6_000L)

internal fun supportsIncognitoProfile(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

/** Borra todo lo que guardaron las pestañas de incógnito (cookies, caché y almacenamiento del sitio). */
internal fun deleteIncognitoProfile() {
    if (!supportsIncognitoProfile()) return
    runCatching { ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE) }
}

/**
 * Crea un WebView endurecido:
 * - sin puentes de JavaScript hacia la app (nada de addJavascriptInterface);
 * - sin acceso a archivos ni a contenido del teléfono;
 * - sin contenido mixto, sin permisos de cámara, micrófono ni ubicación;
 * - Safe Browsing activado y solo http(s) como esquema de navegación.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createBrowserWebView(context: Context, tab: BrowserTab, callbacks: WebViewCallbacks): WebView {
    val mainHandler = Handler(Looper.getMainLooper())
    val tabId = tab.id

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // La pestaña de incógnito usa un perfil aparte, que se borra entero al cerrarla.
        if (tab.incognito && supportsIncognitoProfile()) {
            runCatching { WebViewCompat.setProfile(this, INCOGNITO_PROFILE) }
        }

        settings.apply {
            javaScriptEnabled = true // Casi todos los sitios de video lo necesitan.
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = true
            @Suppress("DEPRECATION")
            saveFormData = false
            if (tab.incognito) cacheMode = WebSettings.LOAD_NO_CACHE
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, true)
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when (val decision = callbacks.decide(url)) {
                    is NavigationDecision.Allow -> {
                        // http se carga siempre como https.
                        if (decision.upgraded && request.isForMainFrame) {
                            view.loadUrl(decision.url)
                            true
                        } else {
                            !request.isForMainFrame && decision.upgraded
                        }
                    }
                    else -> {
                        if (request.isForMainFrame) callbacks.onBlocked(decision)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                callbacks.onPageStarted(tabId, url)
                callbacks.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
            }

            override fun onPageFinished(view: WebView, url: String) {
                callbacks.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
                callbacks.onPageFinished(tabId, url, view.title)
                // Sin addJavascriptInterface: el script solo devuelve un resultado a esta llamada.
                view.evaluateJavascript(MediaSniffer.DOM_SCRIPT) { callbacks.onDomResult(tabId, it) }
                // Muchas páginas empiezan a reproducir un poco después: se vuelve a revisar dos veces.
                RESCAN_DELAYS_MS.forEach { delayMs ->
                    mainHandler.postDelayed({
                        runCatching { view.evaluateJavascript(MediaSniffer.DOM_SCRIPT) { callbacks.onDomResult(tabId, it) } }
                    }, delayMs)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                callbacks.onUrlChanged(tabId, url)
                callbacks.onNavigationState(tabId, view.canGoBack(), view.canGoForward())
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) callbacks.onError(tabId, true)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // Nunca se continúa con un certificado inválido.
                callbacks.onError(tabId, true)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                // Videos y audios directos (.mp4, .m3u8, .mp3...) se detectan por su dirección. Las imágenes sueltas no:
                // se descargan manteniéndolas presionadas o desde "Imágenes de la página".
                if (MediaSniffer.isMediaUrl(url)) mainHandler.post { callbacks.onMediaSniffed(tabId, url) }
                return null
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                callbacks.onError(tabId, true)
                return true // La app no se cierra: la pestaña muestra el error y se puede recargar.
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = callbacks.onProgress(tabId, newProgress)

            override fun onReceivedTitle(view: WebView, title: String) = callbacks.onTitle(tabId, title)

            // Cámara, micrófono y demás se niegan siempre. El pedido de contenido protegido
            // (EME/Widevine) solo sirve para avisar de que no se puede descargar.
            override fun onPermissionRequest(request: PermissionRequest) {
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources) callbacks.onDrm(tabId)
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                callbacks.onShowCustomView(view) { callback.onCustomViewHidden() }
            }

            override fun onHideCustomView() = callbacks.onHideCustomView()
        }

        // Mantener presionada una imagen (o un enlace que contiene una imagen) ofrece descargarla.
        setOnLongClickListener { view ->
            val hit = (view as WebView).hitTestResult
            val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val source = hit.extra
            if (isImage && !source.isNullOrBlank()) {
                callbacks.onImageLongPress(tabId, source)
                true
            } else {
                false
            }
        }

        // Archivos servidos para descargar (no páginas): se analizan como cualquier enlace.
        setDownloadListener { url, _, _, _, _ -> callbacks.onDownload(url) }

        if (tab.url.isNotEmpty()) loadUrl(tab.url)
    }
}

/** Guarda los WebView de las pestañas abiertas y los destruye al cerrarlas. */
internal class WebViewPool(
    private val context: Context,
    private val callbacks: WebViewCallbacks,
) {
    private val views = mutableMapOf<String, WebView>()

    fun getOrCreate(tab: BrowserTab): WebView = views.getOrPut(tab.id) { createBrowserWebView(context, tab, callbacks) }

    fun peek(tabId: String): WebView? = views[tabId]

    fun retainOnly(ids: Set<String>) {
        views.keys.filter { it !in ids }.forEach { destroy(it) }
    }

    fun destroyAll() {
        views.keys.toList().forEach { destroy(it) }
    }

    private fun destroy(id: String) {
        views.remove(id)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
    }
}
