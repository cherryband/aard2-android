package space.cherryband.ari.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import space.cherryband.ari.AriApplication
import space.cherryband.ari.R
import space.cherryband.ari.data.BlobDescriptor
import space.cherryband.ari.util.Util
import java.io.ByteArrayInputStream
import java.util.*

class ArticleWebView @SuppressLint("SetJavaScriptEnabled") constructor(
    context: Context,
    attrs: AttributeSet?
) : SearchableWebView(context, attrs) {
    private val styleSwitcherJs: String?
    private val defaultStyleTitle: String
    private val autoStyleTitle: String
    private val externalSchemes: Set<String> = setOf("https", "ftp", "sftp", "mailto", "geo")

    fun isExternal(uri: Uri): Boolean {
        val scheme = uri.scheme
        val host = uri.host
        return scheme != null && (externalSchemes.contains(scheme) ||
                scheme == "http" && host != LOCALHOST)
    }

    private var styleTitles: SortedSet<String> = TreeSet()
    private var currentSlobId: String? = null
    private var currentSlobUri: String? = null
    private val connectivityManager: ConnectivityManager
    private val timer: Timer
    private val applyStylePref: TimerTask
    var forceLoadRemoteContent = false
    val TAG: String = ArticleWebView::class.simpleName.orEmpty()

    @JavascriptInterface
    fun setStyleTitles(titles: Array<String?>) {
        Log.d(TAG, String.format("Got %d style titles", titles.size))
        if (titles.isEmpty()) {
            return
        }
        val newStyleTitlesSet: SortedSet<String> = TreeSet(listOf(*titles))
        if (styleTitles != newStyleTitlesSet) {
            styleTitles = newStyleTitlesSet
            saveAvailableStylesPref(styleTitles)
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            for (title in titles) {
                Log.d(TAG, title!!)
            }
        }
    }

    constructor(context: Context) : this(context, null)

    init {
        connectivityManager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        styleSwitcherJs = AriApplication.jsStyleSwitcher
        val settings = this.settings
        settings.javaScriptEnabled = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        val r = resources
        defaultStyleTitle = r.getString(R.string.default_style_title)
        autoStyleTitle = r.getString(R.string.auto_style_title)
        addJavascriptInterface(this, "\$SLOB")
        timer = Timer()
        val applyStyleRunnable = Runnable { applyStylePref() }
        applyStylePref = object : TimerTask() {
            override fun run() {
                val handler = handler
                handler?.post(applyStyleRunnable)
            }
        }
        this.webViewClient = object : WebViewClient() {
            val noBytes = ByteArray(0)
            val times: MutableMap<String, MutableList<Long>> = HashMap()
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                Log.d(TAG, "onPageStarted: $url")
                if (url.startsWith("about:")) {
                    return
                }
                if (times.containsKey(url)) {
                    Log.d(TAG, "onPageStarted: already ready seen $url")
                    times[url]?.add(System.currentTimeMillis())
                } else {
                    val tsList: MutableList<Long> = ArrayList()
                    tsList.add(System.currentTimeMillis())
                    times[url] = tsList
                    view.loadUrl("javascript:$styleSwitcherJs")
                    try {
                        timer.schedule(applyStylePref, 250, 200)
                    } catch (ex: IllegalStateException) {
                        Log.w(TAG, "Failed to schedule applyStylePref in view " + view.id, ex)
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d(TAG, "onPageFinished: $url")
                if (url.startsWith("about:")) {
                    return
                }
                if (times.containsKey(url)) {
                    val tsList: MutableList<Long> = times[url]!!
                    val ts: Long = tsList.removeAt(tsList.size - 1)
                    Log.d(
                        TAG,
                        "onPageFinished: finished: " + url + " in " + (System.currentTimeMillis() - ts)
                    )
                    if (tsList.isEmpty()) {
                        Log.d(TAG, "onPageFinished: really done with $url")
                        times.remove(url)
                        applyStylePref.cancel()
                    }
                } else {
                    Log.w(TAG, "onPageFinished: Unexpected page finished event for $url")
                }
                view.loadUrl(
                    "javascript:" + styleSwitcherJs +
                            ";\$SLOB.setStyleTitles(\$styleSwitcher.getTitles())"
                )
                applyStylePref()
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val parsed = request.url
                if (parsed.isRelative) {
                    return null
                }
                val host = parsed.host
                if (host == null || host.equals(LOCALHOST, ignoreCase = true)) {
                    return null
                }
                return if (allowRemoteContent()) {
                    null
                } else WebResourceResponse(
                    "text/plain", "UTF-8",
                    ByteArrayInputStream(noBytes)
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                Log.d(
                    TAG, String.format(
                        "shouldOverrideUrlLoading: %s (current %s)",
                        request.url, view.url
                    )
                )
                val uri = request.url
                val url = request.toString()
                val scheme = uri.scheme
                val host = uri.host
                if (isExternal(uri)) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                    getContext().startActivity(browserIntent)
                    return true
                }
                val fragment = uri.fragment
                if (fragment != null) {
                    val current = Uri.parse(view.url)
                    Log.d(TAG, "shouldOverrideUrlLoading URL with fragment: $url")
                    if (scheme == current.scheme && host == current.host && uri.port == current.port && uri.path == current.path) {
                        Log.d(TAG, "NOT overriding loading of same page link $url")
                        return false
                    }
                }
                if (scheme == "http" && host == LOCALHOST && uri.getQueryParameter("blob") == null) {
                    val intent = Intent(getContext(), ArticleCollectionActivity::class.java)
                    intent.data = uri
                    getContext().startActivity(intent)
                    Log.d(TAG, "Overriding loading of $url")
                    return true
                }
                Log.d(TAG, "NOT overriding loading of $url")
                return false
            }
        }
        setOnLongClickListener {
            val hitTestResult = hitTestResult
            val resultType = hitTestResult.type
            Log.d(
                TAG, String.format(
                    "Long tap on element %s (%s)",
                    resultType,
                    hitTestResult.extra
                )
            )
            if (resultType == HitTestResult.SRC_ANCHOR_TYPE ||
                resultType == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                val url = hitTestResult.extra
                val uri = Uri.parse(url)
                if (isExternal(uri)) {
                    val share = Intent(Intent.ACTION_SEND)
                    share.type = "text/plain"
                    share.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    share.putExtra(Intent.EXTRA_TEXT, url)
                    getContext().startActivity(Intent.createChooser(share, "Share Link"))
                    return@setOnLongClickListener true
                }
            }
            false
        }
        applyTextZoomPref()
    }

    fun allowRemoteContent(): Boolean {
        if (forceLoadRemoteContent) {
            return true
        }
        return when (prefs.getString(PREF_REMOTE_CONTENT, PREF_REMOTE_CONTENT_WIFI)) {
            PREF_REMOTE_CONTENT_ALWAYS -> true
            PREF_REMOTE_CONTENT_WIFI -> connectivityManager.activeNetworkInfo?.type in
                    listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET)
            else -> false
        }
    }

    val availableStyles: Array<String>
        get() {
            val prefs = context.getSharedPreferences(
                "userStyles", AppCompatActivity.MODE_PRIVATE
            )
            val data = prefs.all
            val names: MutableList<String> = ArrayList(data.keys)
            Util.sort(names)
            names.addAll(styleTitles)
            names.add(defaultStyleTitle)
            names.add(autoStyleTitle)
            return names.toTypedArray()
        }
    private val isUIDark: Boolean
        get() = when (application.preferredTheme) {
            AriApplication.PREF_UI_THEME_DARK -> {
                true
            }
            AriApplication.PREF_UI_THEME_SYSTEM -> {
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }

    private val autoStyle: String
        get() {
            if (isUIDark) {
                for (title in styleTitles) {
                    val titleLower = title.lowercase(Locale.getDefault())
                    if (titleLower.contains("night") || titleLower.contains("dark")) {
                        return title
                    }
                }
            }
            Log.d(TAG, "Auto style will return $defaultStyleTitle")
            return defaultStyleTitle
        }

    private fun setStyle(styleTitle: String) {
        val js: String
        val prefs = context.getSharedPreferences(
            "userStyles", AppCompatActivity.MODE_PRIVATE
        )
        js = if (prefs.contains(styleTitle)) {
            val css = prefs.getString(styleTitle, "")
            val elementId = currentSlobId
            String.format(
                "javascript:" + AriApplication.jsUserStyle, elementId, css
            )
        } else {
            String.format(
                "javascript:" + AriApplication.jsClearUserStyle + AriApplication.jsSetCannedStyle,
                currentSlobId, styleTitle
            )
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, js)
        }
        this.loadUrl(js)
    }

    private val prefs: SharedPreferences
        get() = application.prefs()

    fun applyTextZoomPref() {
        val textZoom = prefs.getInt(PREF_TEXT_ZOOM, 100)
        val settings = settings
        settings.textZoom = textZoom
    }

    private fun saveTextZoomPref() {
        val textZoom = settings.textZoom
        val e = prefs.edit()
        e.putInt(PREF_TEXT_ZOOM, textZoom)
        val success = e.commit()
        if (!success) {
            Log.w(TAG, "Failed to save article view text zoom pref")
        }
    }

    private fun saveAvailableStylesPref(styleTitles: Set<String>) {
        val editor = prefs.edit()
        editor.putStringSet(PREF_STYLE_AVAILABLE + currentSlobUri, styleTitles)
        val success = editor.commit()
        if (!success) {
            Log.w(TAG, "Failed to save article view available styles pref")
        }
    }

    private fun loadAvailableStylesPref() {
        if (currentSlobUri == null) {
            Log.w(TAG, "Can't load article view available styles pref - slob uri is null")
            return
        }
        Log.d(TAG, "Available styles before pref load: " + styleTitles.size)
        styleTitles = TreeSet(
            prefs.getStringSet(
                PREF_STYLE_AVAILABLE + currentSlobUri,
                HashSet()
            )
        )
        Log.d(TAG, "Loaded available styles: " + styleTitles.size)
    }

    fun saveStylePref(styleTitle: String?) {
        if (currentSlobUri == null) {
            Log.w(TAG, "Can't save article view style pref - slob uri is null")
            return
        }
        val prefName = PREF_STYLE + currentSlobUri
        val editor = prefs.edit()
        editor.putString(prefName, styleTitle)
        val success = editor.commit()
        if (!success) {
            Log.w(TAG, "Failed to save article view style pref")
        }
    }

    private val stylePreferenceValue: String?
        get() = prefs.getString(PREF_STYLE + currentSlobUri, autoStyleTitle)

    private fun isAutoStyle(title: String?): Boolean = title == autoStyleTitle

    @get:JavascriptInterface
    val preferredStyle: String
        get() {
            if (currentSlobUri == null) {
                return ""
            }
            val styleTitle = stylePreferenceValue
            val result = if (isAutoStyle(styleTitle)) autoStyle else styleTitle!!
            Log.d(TAG, "getPreferredStyle() will return $result")
            return result
        }

    @JavascriptInterface
    fun exportStyleSwitcherAs(): String {
        return "\$styleSwitcher"
    }

    @JavascriptInterface
    fun onStyleSet(title: String) {
        Log.d(TAG, "Style set! $title")
        applyStylePref.cancel()
    }

    fun applyStylePref() {
        setStyle(preferredStyle)
    }

    fun textZoomIn(): Boolean {
        val newZoom = settings.textZoom + 20
        return if (newZoom <= 200) {
            settings.textZoom = newZoom
            saveTextZoomPref()
            true
        } else {
            false
        }
    }

    fun textZoomOut(): Boolean {
        val newZoom = settings.textZoom - 20
        return if (newZoom >= 40) {
            settings.textZoom = newZoom
            saveTextZoomPref()
            true
        } else {
            false
        }
    }

    fun resetTextZoom() {
        settings.textZoom = 100
        saveTextZoomPref()
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        beforeLoadUrl(url)
        super.loadUrl(url, additionalHttpHeaders)
    }

    override fun loadUrl(url: String) {
        beforeLoadUrl(url)
        super.loadUrl(url)
    }

    private fun beforeLoadUrl(url: String) {
        setCurrentSlobIdFromUrl(url)
        if (!url.startsWith("javascript:")) {
            updateBackgrounColor()
        }
    }

    private fun updateBackgrounColor() {
        var color = Color.WHITE
        val preferredStyle = preferredStyle.lowercase(Locale.getDefault())
        // webview's default background may "show through" before page
        // load started and/or before page's style applies (and even after that if
        // style doesn't explicitly set background).
        // this is a hack to preemptively set "right" background and prevent
        // extra flash
        //
        // TODO Hack it even more - allow style title to include background color spec
        // so that this can work with "strategically" named user css
        if (preferredStyle.contains("night") || preferredStyle.contains("dark")) {
            color = Color.BLACK
        }
        setBackgroundColor(color)
    }

    private val application: AriApplication
        get() = (context as AppCompatActivity).application as AriApplication

    private fun setCurrentSlobIdFromUrl(url: String) {
        if (!url.startsWith("javascript:")) {
            val uri = Uri.parse(url)
            val bd = BlobDescriptor.fromUri(uri)
            if (bd != null) {
                currentSlobId = bd.slobId
                currentSlobUri = application.getSlobURI(currentSlobId)
                loadAvailableStylesPref()
            } else {
                currentSlobId = null
                currentSlobUri = null
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(
                    TAG, String.format(
                        "currentSlobId set from url %s to %s, uri %s",
                        url, currentSlobId, currentSlobUri
                    )
                )
            }
        }
    }

    override fun destroy() {
        super.destroy()
        timer.cancel()
    }

    companion object {
        const val LOCALHOST = AriApplication.LOCALHOST
        const val PREF = "articleView"
        private const val PREF_TEXT_ZOOM = "textZoom"
        private const val PREF_STYLE = "style."
        private const val PREF_STYLE_AVAILABLE = "style.available."
        const val PREF_REMOTE_CONTENT = "remoteContent"
        const val PREF_REMOTE_CONTENT_ALWAYS = "always"
        const val PREF_REMOTE_CONTENT_WIFI = "wifi"
        const val PREF_REMOTE_CONTENT_NEVER = "never"
    }
}