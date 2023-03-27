package space.cherryband.ari.ui

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerTitleStrip
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import itkach.slob.Slob
import itkach.slob.Slob.PeekableIterator
import space.cherryband.ari.AriApplication
import space.cherryband.ari.R
import space.cherryband.ari.data.BlobDescriptor
import space.cherryband.ari.util.Util

class ArticleCollectionActivity : AppCompatActivity(), OnSystemUiVisibilityChangeListener,
    OnSharedPreferenceChangeListener {
    var articleCollectionPagerAdapter: ArticleCollectionPagerAdapter? = null
    private lateinit var viewPager: ViewPager
    private lateinit var app: AriApplication

    private fun toBlobWithFragment(fragment: String) : (Any?) -> Slob.Blob?
            = { item: Any? ->
                with(item as Slob.Blob){
                    Slob.Blob(owner, id, key, fragment)
                }
            }

    private val blobToBlob: (Any?) -> Slob.Blob? = { item: Any? -> item as Slob.Blob }
    private var onDestroyCalled = false
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as AriApplication
        app.installTheme(this)
        supportActionBar?.hide()
        setContentView(R.layout.activity_article_collection_loading)
        app.push(this)
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        actionBar.subtitle = "..."
        val intent = intent
        val position = intent.getIntExtra("position", 0)
        Thread {
            val result: ArticleCollectionPagerAdapter?
            val articleUrl = intent.data
            try {
                result = if (articleUrl != null) {
                    createFromUri(app, articleUrl)
                } else {
                    when (intent.action) {
                        null -> createFromLastResult(app)
                        "showBookmarks" -> createFromBookmarks(app)
                        "showHistory" -> createFromHistory(app)
                        else -> createFromIntent(app, intent)
                    }
                }
                runOnUiThread { doWithAdapter(result, position) }
            } catch (e: Exception) {
                runOnUiThread { e.localizedMessage?.let { toastAndQuit(it) } }
            }
        }.start()
    }

    private fun toastAndQuit(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun toastAndQuit(id: Int) {
        Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun doWithAdapter(adapter: ArticleCollectionPagerAdapter?, position: Int) {
        if (isFinishing || onDestroyCalled) {
            return
        }
        if (adapter == null) {
            toastAndQuit(R.string.article_collection_invalid_link)
            return
        }
        articleCollectionPagerAdapter = adapter
        val count = articleCollectionPagerAdapter!!.count
        if (count == 0) {
            toastAndQuit(R.string.article_collection_nothing_found)
            return
        }
        if (position > count - 1) {
            toastAndQuit(R.string.article_collection_selected_not_available)
            return
        }
        setContentView(R.layout.activity_article_collection)
        val titleStrip = findViewById<PagerTitleStrip>(R.id.pager_title_strip)
        titleStrip.visibility = if (count == 1) ViewGroup.GONE else ViewGroup.VISIBLE
        viewPager = findViewById(R.id.pager)
        viewPager.adapter = articleCollectionPagerAdapter
        viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrollStateChanged(arg0: Int) {}
            override fun onPageScrolled(arg0: Int, arg1: Float, arg2: Int) {}
            override fun onPageSelected(position: Int) {
                updateTitle(position)
                runOnUiThread {
                    val fragment =
                        articleCollectionPagerAdapter!!.getItem(position) as ArticleFragment
                    fragment.applyTextZoomPref()
                }
            }
        })
        viewPager.currentItem = position
        titleStrip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
        updateTitle(position)
        articleCollectionPagerAdapter!!.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                if (articleCollectionPagerAdapter!!.count == 0) {
                    finish()
                }
            }
        })
    }

    private fun createFromUri(
        app: AriApplication,
        articleUrl: Uri
    ): ArticleCollectionPagerAdapter? {
        val host = articleUrl.host
        if (!(host == "localhost" || host!!.matches(Regex("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")))) {
            return createFromIntent(app, intent)
        }
        val bd = BlobDescriptor.fromUri(articleUrl) ?: return null
        val result: Iterator<Slob.Blob> = app.find(bd.key, bd.slobId)
        val data = BlobListAdapter(this, 20, 1)
        data.setData(result)
        val fragment = bd.fragment
        return ArticleCollectionPagerAdapter(
            app, data,
            if (!fragment.isNullOrBlank()) toBlobWithFragment(fragment) else blobToBlob,
            supportFragmentManager
        )
    }

    private fun createFromLastResult(app: AriApplication): ArticleCollectionPagerAdapter {
        return ArticleCollectionPagerAdapter(
            app,
            app.lastResult,
            blobToBlob,
            supportFragmentManager
        )
    }

    private fun createFromBookmarks(app: AriApplication): ArticleCollectionPagerAdapter {
        return ArticleCollectionPagerAdapter(
            app,
            BlobDescriptorListAdapter(app.bookmarks),
            { item: Any? -> app.bookmarks.resolve(item as BlobDescriptor) },
            supportFragmentManager
        )
    }

    private fun createFromHistory(app: AriApplication): ArticleCollectionPagerAdapter {
        return ArticleCollectionPagerAdapter(
            app,
            BlobDescriptorListAdapter(app.history),
            { item: Any? -> app.history.resolve(item as BlobDescriptor) },
            supportFragmentManager
        )
    }

    private fun createFromIntent(
        app: AriApplication,
        intent: Intent
    ): ArticleCollectionPagerAdapter {
        var lookupKey = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            lookupKey = getIntent()
                .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra(SearchManager.QUERY)
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra("EXTRA_QUERY")
        }
        var preferredSlobId: String? = null
        if (lookupKey == null) {
            val uri = intent.data
            val segments = uri!!.pathSegments
            val length = segments.size
            if (length > 0) {
                lookupKey = segments[length - 1]
            }
            val slobUri = Util.wikipediaToSlobUri(uri)
            Log.d(TAG, String.format("Converted URI %s to slob URI %s", uri, slobUri))
            if (slobUri != null) {
                val slob = app.findSlob(slobUri)
                if (slob != null) {
                    preferredSlobId = slob.id.toString()
                    Log.d(
                        TAG,
                        String.format("Found slob %s for slob URI %s", preferredSlobId, slobUri)
                    )
                }
            }
        }
        val data = BlobListAdapter(this, 20, 1)
        if (lookupKey.isNullOrBlank()) {
            val msg = getString(R.string.article_collection_nothing_to_lookup)
            throw RuntimeException(msg)
        } else {
            val result = stemLookup(app, lookupKey, preferredSlobId)
            data.setData(result)
        }
        return ArticleCollectionPagerAdapter(
            app, data, blobToBlob, supportFragmentManager
        )
    }

    private fun stemLookup(
        app: AriApplication,
        lookupKey: String,
        preferredSlobId: String?
    ): Iterator<Slob.Blob> {
        var result: PeekableIterator<Slob.Blob>
        val length = lookupKey.length
        var currentLookupKey = lookupKey
        var currentLength = currentLookupKey.length
        do {
            result = app.find(currentLookupKey, preferredSlobId, true)
            if (result.hasNext()) {
                val b = result.peek()
                if (b.key.length - length > 3) {
                    //we don't like this result
                } else {
                    break
                }
            }
            currentLookupKey = currentLookupKey.substring(0, currentLength - 1)
            currentLength = currentLookupKey.length
        } while (length - currentLength < 5 && currentLength > 0)
        return result
    }

    private fun updateTitle(position: Int) {
        Log.d("updateTitle", "" + position + " count: " + articleCollectionPagerAdapter!!.count)
        val blob = articleCollectionPagerAdapter?.get(position)
        val pageTitle = articleCollectionPagerAdapter!!.getPageTitle(position)
        Log.d("updateTitle", "" + blob)
        val actionBar = supportActionBar
        if (blob != null) {
            val dictLabel = blob.owner.tags["label"]
            actionBar?.title = dictLabel
            app.history.add(app.getUrl(blob))
        } else {
            actionBar?.setTitle("???")
        }
        actionBar!!.subtitle = pageTitle
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREF_FULLSCREEN) {
            applyFullScreenPref()
        }
    }

    private fun applyFullScreenPref() {
        if (fullScreenPref) {
            fullScreen()
        } else {
            unFullScreen()
        }
    }

    private fun prefs() = app.prefs()

    private var fullScreenPref: Boolean
        get() = prefs().getBoolean(PREF_FULLSCREEN, false)
        private set(value) = app.setBooleanPref(PREF_FULLSCREEN, value)

    private fun fullScreen() {
        Log.d(TAG, "[F] fullscreen")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE)
        supportActionBar?.hide()
    }

    private fun unFullScreen() {
        Log.d(TAG, "[F] unfullscreen")
        window.decorView.systemUiVisibility = 0
        supportActionBar?.show()
    }

    fun toggleFullScreen() {
        fullScreenPref = !fullScreenPref
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[F] Resume")
        applyFullScreenPref()
        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener(this)
        prefs().registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "[F] Pause")
        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener(null)
        prefs().unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        onDestroyCalled = true
        viewPager.adapter = null
        articleCollectionPagerAdapter?.destroy()
        app.pop(this)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val upIntent = Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                TaskStackBuilder.create(this)
                    .addNextIntent(upIntent).startActivities()
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(upIntent)
            }
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onSystemUiVisibilityChange(visibility: Int) {
        if (isFinishing) {
            return
        }
        val decorView = window.decorView
        val uiOptions = decorView.systemUiVisibility
        val isHideNavigation = uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == uiOptions
        if (!isHideNavigation) {
            fullScreenPref = false
        }
    }

    private val isVolumeForNavDisabled: Boolean
        get() = !app.useVolumeForNav

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCanceled) {
            return true
        }
        if (articleCollectionPagerAdapter == null) {
            return false
        }
        val af = articleCollectionPagerAdapter!!.primaryItem
        if (af != null) {
            val webView = af.webView
            if (webView != null) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (isVolumeForNavDisabled) {
                        return false
                    }
                    val scrolled = webView.pageUp(false)
                    if (!scrolled) {
                        val current = viewPager.currentItem
                        if (current > 0) {
                            viewPager.currentItem = current - 1
                        } else {
                            finish()
                        }
                    }
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (isVolumeForNavDisabled) {
                        return false
                    }
                    val scrolled = webView.pageDown(false)
                    if (!scrolled) {
                        val current = viewPager.currentItem
                        if (current < articleCollectionPagerAdapter!!.count - 1) {
                            viewPager.currentItem = current + 1
                        }
                    }
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isVolumeForNavDisabled) {
                return false
            }
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeForNavDisabled) {
            return false
        }
        val af = articleCollectionPagerAdapter!!.primaryItem
        if (af != null) {
            val webView = af.webView
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                webView?.pageUp(true)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                webView?.pageDown(true)
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }


    class ArticleCollectionPagerAdapter(
        private var app: AriApplication?,
        private var data: BaseAdapter?,
        private val toBlob: (Any?) -> Slob.Blob?,
        fm: FragmentManager
    ) : FragmentStatePagerAdapter(fm) {
        private var count: Int = data!!.count
        private val observer = object : DataSetObserver() {
            override fun onChanged() {
                count = data!!.count
                notifyDataSetChanged()
            }
        }
        var primaryItem: ArticleFragment? = null
            private set

        init {
            data!!.registerDataSetObserver(observer)
        }

        fun destroy() {
            data!!.unregisterDataSetObserver(observer)
            data = null
            app = null
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            super.setPrimaryItem(container, position, `object`)
            primaryItem = `object` as ArticleFragment
        }

        override fun getItem(i: Int): Fragment {
            val fragment: Fragment = ArticleFragment()
            val blob = get(i)
            if (blob != null) {
                val articleUrl = app!!.getUrl(blob)
                val args = Bundle()
                args.putString(ArticleFragment.ARG_URL, articleUrl)
                fragment.arguments = args
            }
            return fragment
        }

        override fun getCount(): Int = count

        operator fun get(position: Int): Slob.Blob? = toBlob(data!!.getItem(position))

        override fun getPageTitle(position: Int): CharSequence? {
            if (position < data!!.count) {
                val item = data!!.getItem(position)
                if (item is BlobDescriptor) {
                    return item.key
                }
                if (item is Slob.Blob) {
                    return item.key
                }
            }
            return "???"
        }

        //this is needed so that fragment is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        override fun getItemPosition(`object`: Any): Int {
            return POSITION_NONE
        }
    }

    companion object {
        private val TAG = ArticleCollectionActivity::class.java.simpleName
        const val PREF_FULLSCREEN = "fullscreen"
    }
}