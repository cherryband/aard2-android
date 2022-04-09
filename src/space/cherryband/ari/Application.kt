package space.cherryband.ari

import itkach.slobber.Slobber
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import android.database.DataSetObserver
import itkach.slob.Slob
import android.content.SharedPreferences
import kotlin.jvm.JvmOverloads
import itkach.slob.Slob.PeekableIterator
import android.os.AsyncTask
import android.content.pm.PackageManager
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import kotlin.Throws
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.lang.Exception
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.floor

class Application : android.app.Application() {
    private lateinit var slobber: Slobber
    private lateinit var articleActivities: MutableList<AppCompatActivity>
    private var port = -1
    internal lateinit var bookmarks: BlobDescriptorList
    internal lateinit var history: BlobDescriptorList
    internal lateinit var dictionaries: SlobDescriptorList
    internal lateinit var lastResult: BlobListAdapter
    var lookupQuery = ""
        private set
    override fun onCreate() {
        super.onCreate()
        try {
            val setWebContentsDebuggingEnabledMethod = WebView::class.java.getMethod(
                    "setWebContentsDebuggingEnabled", Boolean::class.javaPrimitiveType)
            setWebContentsDebuggingEnabledMethod.invoke(null, true)
        } catch (e1: NoSuchMethodException) {
            Log.d(TAG, "setWebContentsDebuggingEnabledMethod method not found")
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        articleActivities = Collections.synchronizedList(ArrayList())
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val dictStore = DescriptorStore<SlobDescriptor>(mapper, getDir("dictionaries", MODE_PRIVATE))
        val bookmarkStore = DescriptorStore<BlobDescriptor>(mapper, getDir("bookmarks", MODE_PRIVATE))
        val historyStore = DescriptorStore<BlobDescriptor>(mapper, getDir("history", MODE_PRIVATE))
        slobber = Slobber()
        val t0 = System.currentTimeMillis()
        startWebServer()
        Log.d(TAG,"Started web server on port $port in ${System.currentTimeMillis() - t0} ms")
        try {
            javaClass.classLoader?.getResourceAsStream("styleswitcher.js").let {
                jsStyleSwitcher = readTextFile(it, 0)
            }
            assets.open("userstyle.js").let {
                jsUserStyle = readTextFile(it, 0)
            }
            assets.open("clearuserstyle.js").let {
                jsClearUserStyle = readTextFile(it, 0)
            }
            assets.open("setcannedstyle.js").let {
                jsSetCannedStyle = readTextFile(it, 0)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        val initialQuery = prefs().getString("query", "")
        lastResult = BlobListAdapter(this)
        dictionaries = SlobDescriptorList(this, dictStore)
        bookmarks = BlobDescriptorList(this, bookmarkStore)
        history = BlobDescriptorList(this, historyStore)
        dictionaries.registerDataSetObserver(object : DataSetObserver() {
            @Synchronized
            override fun onChanged() {
                lastResult.setData(Collections.emptyIterator())
                slobber.setSlobs(null)
                val slobs: MutableList<Slob> = dictionaries
                        .mapNotNull { it.load(applicationContext) }
                        .toMutableList()
                slobber.setSlobs(slobs)
                EnableLinkHandling().execute(*activeSlobs)
                lookup(lookupQuery)
                bookmarks.notifyDataSetChanged()
                history.notifyDataSetChanged()
            }
        })
        dictionaries.load()
        lookup(initialQuery, false)
        bookmarks.load()
        history.load()
    }

    private fun startWebServer() {
        var portCandidate = PREFERRED_PORT
        try {
            slobber.start("127.0.0.1", portCandidate)
            port = portCandidate
        } catch (e: IOException) {
            Log.w(TAG, "Failed to start on preferred port $portCandidate", e)
            val seen: MutableSet<Int> = HashSet()
            seen += PREFERRED_PORT
            val rand = Random()
            var attemptCount = 0
            while (true) {
                val value = 1 + floor((65535 - 1025) * rand.nextDouble()).toInt()
                portCandidate = 1024 + value
                if (seen.contains(portCandidate)) {
                    continue
                }
                attemptCount += 1
                seen += portCandidate
                var lastError: Exception?
                try {
                    slobber.start("127.0.0.1", portCandidate)
                    port = portCandidate
                    break
                } catch (e1: IOException) {
                    lastError = e1
                    Log.w(TAG, "Failed to start on preferred port $portCandidate", e1)
                }
                if (attemptCount >= 20) {
                    throw RuntimeException("Failed to start web server", lastError)
                }
            }
        }
    }

    fun prefs(): SharedPreferences = getSharedPreferences(PREF, MODE_PRIVATE)

    val preferredTheme: String?
        get() = prefs().getString(PREF_UI_THEME, PREF_UI_THEME_SYSTEM)

    fun installTheme(activity: AppCompatActivity) {
        when (preferredTheme) {
            PREF_UI_THEME_DARK -> activity.setTheme(R.style.Theme_App_Dark)
            PREF_UI_THEME_LIGHT -> activity.setTheme(R.style.Theme_App_Light)
            else -> activity.setTheme(R.style.Theme_App)
        }
    }

    fun push(activity: AppCompatActivity) {
        articleActivities.add(activity)
        Log.d(TAG, "Activity added, stack size ${articleActivities.size}")
        if (articleActivities.size > 3) {
            Log.d(TAG, "Max stack size exceeded, finishing oldest activity")
            articleActivities[0].finish()
        }
    }

    fun pop(activity: AppCompatActivity) {
        articleActivities.remove(activity)
    }

    val activeSlobs: Array<Slob>
        get() {
            val result: MutableList<Slob> = ArrayList(dictionaries.size)
            dictionaries
                    .asSequence()
                    .filter { it.active }
                    .mapNotNullTo(result) { slobber.getSlob(it.id) }
            return result.toTypedArray()
        }
    private val favoriteSlobs: Array<Slob>
        get() {
            val result: MutableList<Slob> = ArrayList(dictionaries.size)
            dictionaries
                    .asSequence()
                    .filter { it.active && it.priority > 0 }
                    .mapNotNullTo(result) { slobber.getSlob(it.id) }
            return result.toTypedArray()
        }

    fun find(key: String?): Iterator<Slob.Blob> = Slob.find(key, *activeSlobs)

    //When following links we want to consider all dictionaries
    //including the ones user turned off
    fun find(key: String?, preferredSlobId: String?): Iterator<Slob.Blob> =
            find(key, preferredSlobId, false)

    @JvmOverloads
    fun find(key: String?, preferredSlobId: String?, activeOnly: Boolean, upToStrength: Slob.Strength? = null):
            PeekableIterator<Slob.Blob> {
        val t0 = System.currentTimeMillis()
        val slobs = if (activeOnly) activeSlobs else slobber.slobs
        val result = Slob.find(key, slobs, slobber.findSlob(preferredSlobId), upToStrength)
        Log.d(TAG, "find ran in ${System.currentTimeMillis() - t0}ms")
        return result
    }

    var isOnlyFavDictsForRandomLookup: Boolean
        get() {
            val prefs = prefs()
            return prefs.getBoolean(PREF_RANDOM_FAV_LOOKUP, false)
        }
        set(value) {
            val prefs = prefs()
            val editor = prefs.edit()
            editor.putBoolean(PREF_RANDOM_FAV_LOOKUP, value)
            editor.apply()
        }

    fun random(): Slob.Blob {
        val slobs = if (isOnlyFavDictsForRandomLookup) favoriteSlobs else activeSlobs
        return slobber.findRandom(slobs)
    }

    fun useVolumeForNav(): Boolean = prefs().getBoolean(PREF_USE_VOLUME_FOR_NAV, true)

    fun setUseVolumeForNav(value: Boolean) {
        prefs().edit().run{
            putBoolean(PREF_USE_VOLUME_FOR_NAV, value)
            apply()
        }
    }

    fun autoPaste(): Boolean = prefs().getBoolean(PREF_AUTO_PASTE, false)

    fun setAutoPaste(value: Boolean) {
        prefs().edit().run {
            putBoolean(PREF_AUTO_PASTE, value)
            apply()
        }
    }

    fun getUrl(blob: Slob.Blob?): String {
        return String.format(CONTENT_URL_TEMPLATE,
                port, Slobber.mkContentURL(blob))
    }

    fun getSlob(slobId: String?): Slob = slobber.getSlob(slobId)

    @Synchronized
    fun addDictionary(uri: Uri): Boolean {
        val newDesc = SlobDescriptor.fromUri(applicationContext, uri.toString())
        if (newDesc.id != null) {
            return dictionaries.any { d -> d.id != null && d.id == newDesc.id }
        }
        dictionaries.add(newDesc)
        return false
    }

    fun findSlob(slobOrUri: String?): Slob = slobber.findSlob(slobOrUri)

    fun getSlobURI(slobId: String?): String = slobber.getSlobURI(slobId)

    fun addBookmark(contentURL: String?) {
        bookmarks.add(contentURL)
    }

    fun removeBookmark(contentURL: String?) {
        bookmarks.remove(contentURL)
    }

    fun isBookmarked(contentURL: String?): Boolean = bookmarks.contains(contentURL)

    private fun setLookupResult(query: String, data: Iterator<Slob.Blob>) {
        lastResult.setData(data)
        lookupQuery = query
        val edit = prefs().edit()
        edit.putString("query", query)
        edit.apply()
    }

    private var currentLookupTask: AsyncTask<Void?, Void?, Iterator<Slob.Blob>>? = null
    @JvmOverloads
    fun lookup(query: String?, async: Boolean = true) {
        if (currentLookupTask != null) {
            currentLookupTask!!.cancel(false)
            notifyLookupCanceled(query)
            currentLookupTask = null
        }
        notifyLookupStarted(query)
        if (query == null || query == "") {
            setLookupResult("", Collections.emptyIterator())
            notifyLookupFinished(query)
            return
        }
        if (async) {
            currentLookupTask = object : AsyncTask<Void?, Void?, Iterator<Slob.Blob>>() {
                override fun doInBackground(vararg params: Void?): Iterator<Slob.Blob> {
                    return find(query)
                }

                override fun onPostExecute(result: Iterator<Slob.Blob>) {
                    if (!isCancelled) {
                        setLookupResult(query, result)
                        notifyLookupFinished(query)
                        currentLookupTask = null
                    }
                }
            }
            currentLookupTask?.execute()
        } else {
            setLookupResult(query, find(query))
            notifyLookupFinished(query)
        }
    }

    private fun notifyLookupStarted(query: String?) {
        lookupListeners.forEach { l -> l.onLookupStarted(query) }
    }

    private fun notifyLookupFinished(query: String?) {
        lookupListeners.forEach { l -> l.onLookupFinished(query) }
    }

    private fun notifyLookupCanceled(query: String?) {
        lookupListeners.forEach { l -> l.onLookupCanceled(query) }
    }

    private val lookupListeners: MutableList<LookupListener> = ArrayList()
    fun addLookupListener(listener: LookupListener) {
        lookupListeners += listener
    }

    fun removeLookupListener(listener: LookupListener) {
        lookupListeners.remove(listener)
    }

    internal class FileTooBigException : IOException()
    private inner class EnableLinkHandling : AsyncTask<Slob, Void?, Void?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(slobs: Array<Slob>): Void? {
            val hosts: MutableSet<String> = HashSet()
            for (slob in slobs) {
                try {
                    val uriValue = slob.tags["uri"]
                    val uri = Uri.parse(uriValue)
                    val host = uri.host
                    if (host != null) {
                        hosts.add(host.lowercase(Locale.getDefault()))
                    }
                } catch (ex: Exception) {
                    Log.w(TAG, String.format("Dictionary %s (%s) has no uri tag", slob.id, slob.tags), ex)
                }
            }
            var t0 = System.currentTimeMillis()
            val packageName = packageName
            try {
                val pm = packageManager
                val p = pm.getPackageInfo(packageName,
                        PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
                Log.d(TAG, "Done getting available activities in ${System.currentTimeMillis() - t0}")
                t0 = System.currentTimeMillis()
                for (activityInfo in p.activities) {
                    if (isCancelled) break
                    if (activityInfo.targetActivity != null) {
                        val enabled = hosts.contains(activityInfo.name)
                        var setting = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        if (enabled) {
                            Log.d(TAG, "Enabling links handling for ${activityInfo.name}")
                            setting = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        }
                        pm.setComponentEnabledSetting(ComponentName(applicationContext, activityInfo.name),
                                setting, PackageManager.DONT_KILL_APP)
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, e)
            }
            Log.d(TAG, "Done enabling activities in ${System.currentTimeMillis() - t0}")
            return null
        }
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"
        const val CONTENT_URL_TEMPLATE = "http://$LOCALHOST:%s%s"
        private const val PREFERRED_PORT = 8013
        lateinit var jsStyleSwitcher: String
        lateinit var jsUserStyle: String
        lateinit var jsClearUserStyle: String
        lateinit var jsSetCannedStyle: String
        private const val PREF = "app"
        const val PREF_RANDOM_FAV_LOOKUP = "onlyFavDictsForRandomLookup"
        const val PREF_UI_THEME = "UITheme"
        const val PREF_UI_THEME_LIGHT = "light"
        const val PREF_UI_THEME_DARK = "dark"
        const val PREF_UI_THEME_SYSTEM = "auto"
        const val PREF_USE_VOLUME_FOR_NAV = "useVolumeForNav"
        const val PREF_AUTO_PASTE = "autoPaste"
        private val TAG = Application::class.java.simpleName
        @JvmStatic
        @Throws(IOException::class)
        fun readTextFile(stream: InputStream?, maxSize: Int): String {
            val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
            val sw = StringWriter()
            val buf = CharArray(16384)
            var count = 0
            while (true) {
                val read = reader.read(buf)
                if (read == -1) {
                    break
                }
                count += read
                if (maxSize in 1 until count) {
                    throw FileTooBigException()
                }
                sw.write(buf, 0, read)
            }
            reader.close()
            return sw.toString()
        }
    }
}