package space.cherryband.ari.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import space.cherryband.ari.AriApplication.Companion.getApp
import space.cherryband.ari.R
import space.cherryband.ari.util.IconMaker

class ArticleFragment : Fragment() {
    var webView: ArticleWebView? = null
        private set
    private lateinit var miBookmark: MenuItem
    private lateinit var miFullscreen: MenuItem
    private val icBookmark: Drawable by lazy { IconMaker.actionBar(activity, IconMaker.IC_BOOKMARK) }
    private val icBookmarkO: Drawable by lazy { IconMaker.actionBar(activity, IconMaker.IC_BOOKMARK_O) }
    private val icFullscreen: Drawable by lazy { IconMaker.actionBar(activity, IconMaker.IC_FULLSCREEN) }
    private var url: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        //Looks like this may be called multiple times with the same menu
        //for some reason when activity is restored, so need to clear
        //to avoid duplicates
        menu.clear()
        inflater.inflate(R.menu.article, menu)
        miBookmark = menu.findItem(R.id.action_bookmark_article)
        miFullscreen = menu.findItem(R.id.action_fullscreen).apply {
            isVisible = false
            isEnabled = false
        }
    }

    private fun displayBookmarked(value: Boolean) {
        miBookmark.apply {
            isChecked = value
            icon = if (value) icBookmark else icBookmarkO
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_find_in_page -> {
                webView?.showFind(null, true)
                true
            }
            R.id.action_bookmark_article -> {
                val app = getApp(activity)
                if (url != null) {
                    if (item.isChecked) {
                        app.removeBookmark(url)
                        displayBookmarked(false)
                    } else {
                        app.addBookmark(url)
                        displayBookmarked(true)
                    }
                }
                true
            }
            R.id.action_fullscreen -> {
                (requireActivity() as ArticleCollectionActivity).toggleFullScreen()
                true
            }
            R.id.action_zoom_in -> {
                webView?.textZoomIn()
                true
            }
            R.id.action_zoom_out -> {
                webView?.textZoomOut()
                true
            }
            R.id.action_zoom_reset -> {
                webView?.resetTextZoom()
                true
            }
            R.id.action_load_remote_content -> {
                webView?.apply {
                    forceLoadRemoteContent = true
                    reload()
                }
                true
            }
            R.id.action_select_style -> {
                webView?.let{
                    val styleTitles = it.availableStyles
                    AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.select_style)
                        .setItems(styleTitles) { _, which: Int ->
                            val title = styleTitles[which]
                            it.saveStylePref(title)
                            it.applyStylePref()
                        }
                        .create()
                        .show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val args = arguments
        url = args?.getString(ARG_URL)

        if (url == null) {
            val layout = inflater.inflate(R.layout.empty_view, container, false)
            val textView: TextView = layout.findViewById(R.id.empty_text)
            textView.text = ""
            val icon: ImageView = layout.findViewById(R.id.empty_icon)
            icon.setImageDrawable(IconMaker.emptyView(activity, IconMaker.IC_BAN))
            setHasOptionsMenu(false)
            return layout
        }

        val layout = inflater.inflate(R.layout.article_view, container, false)
        val progressBar: ProgressBar = layout.findViewById(R.id.webViewPogress)

        webView = layout.findViewById<ArticleWebView>(R.id.webView).apply {
            savedInstanceState?.let { restoreState(it) }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    val activity: Activity? = activity
                    activity?.runOnUiThread {
                        progressBar.progress = newProgress
                        if (newProgress >= progressBar.max) {
                            progressBar.visibility = ViewGroup.GONE
                        }
                    }
                }
            }
        }
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        url?.let{ webView?.loadUrl(it) }
    }
    override fun onResume() {
        super.onResume()
        applyTextZoomPref()
        applyStylePref()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (url == null) {
            miBookmark.isVisible = false
        } else {
            val app = getApp(activity)
            try {
                val bookmarked = app.isBookmarked(url)
                displayBookmarked(bookmarked)
            } catch (ex: Exception) {
                miBookmark.isVisible = false
            }
        }
        applyTextZoomPref()
        applyStylePref()
        miFullscreen.icon = icFullscreen
    }

    fun applyTextZoomPref() {
        webView?.applyTextZoomPref()
    }

    private fun applyStylePref() {
        webView?.applyStylePref()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        const val ARG_URL = "articleUrl"
    }
}