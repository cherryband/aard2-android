@file:Suppress("unused")

package space.cherryband.ari

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.DataSetObserver
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import itkach.slob.Slob
import itkach.slob.Slob.PeekableIterator
import itkach.slobber.Slobber
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.cherryband.ari.data.*
import space.cherryband.ari.ui.BlobListAdapter
import space.cherryband.ari.ui.LookupListener
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class AriApplication : Application() {
    private val appScope = MainScope()
    private val mapper = ObjectMapper()
    private val slobber = Slobber()
    
    lateinit var dictionaries: SlobDescriptorList
    lateinit var lastResult: BlobListAdapter
    lateinit var bookmarks: BlobDescriptorList
    lateinit var history: BlobDescriptorList
    var lookupQuery = ""
        private set

    
    private var port = -1
    private lateinit var bookmarkStore: DescriptorStore<BlobDescriptor>
    private lateinit var historyStore: DescriptorStore<BlobDescriptor>
    private lateinit var dictStore: DescriptorStore<SlobDescriptor>
    private lateinit var articleActivities: MutableList<AppCompatActivity>
    
    override fun onCreate() {
        super.onCreate()
        try {
            val setWebContentsDebuggingEnabledMethod = WebView::class.java.getMethod(
                "setWebContentsDebuggingEnabled", Boolean::class.javaPrimitiveType
            )
            setWebContentsDebuggingEnabledMethod.invoke(null, true)
        } catch (e1: NoSuchMethodException) {
            Log.d(
                TAG,
                "setWebContentsDebuggingEnabledMethod method not found"
            )
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        articleActivities = Collections.synchronizedList(ArrayList())
        mapper.configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )
        dictStore = DescriptorStore(
            mapper,
            getDir("dictionaries", MODE_PRIVATE)
        )
        bookmarkStore = DescriptorStore(
            mapper,
            getDir("bookmarks", MODE_PRIVATE)
        )
        historyStore = DescriptorStore(
            mapper,
            getDir("history", MODE_PRIVATE)
        )
        val t0 = System.currentTimeMillis()
        startWebServer()
        Log.d(
            TAG, 
            "Started web server on port $port in ${System.currentTimeMillis() - t0} ms"
        )
        try {
            var stream: InputStream? = Objects.requireNonNull(javaClass.classLoader)
                .getResourceAsStream("styleswitcher.js")
            jsStyleSwitcher = readTextFile(stream, 0)
            stream = assets.open("userstyle.js")
            jsUserStyle = readTextFile(stream, 0)
            stream = assets.open("clearuserstyle.js")
            jsClearUserStyle = readTextFile(stream, 0)
            stream = assets.open("setcannedstyle.js")
            jsSetCannedStyle = readTextFile(stream, 0)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        val initialQuery = prefs().getString("query", "")
        lastResult = BlobListAdapter(this)
        dictionaries =
            SlobDescriptorList(this, dictStore)
        bookmarks =
            BlobDescriptorList(this, bookmarkStore)
        history =
            BlobDescriptorList(this, historyStore)
        dictionaries.registerDataSetObserver(object : DataSetObserver() {
            @Synchronized
            override fun onChanged() {
                lastResult.setData(Collections.emptyIterator())
                slobber.setSlobs(null)
                val slobs: MutableList<Slob> = ArrayList()
                for (sd in dictionaries) {
                    val s = sd.load(applicationContext)
                    if (s != null) {
                        slobs.add(s)
                    }
                }
                slobber.setSlobs(slobs)
                appScope.launch { enableLinkHandling(activeSlobs) }
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
            Log.w(TAG, "Failed to start on port $portCandidate", e)
            val seen: MutableSet<Int> = HashSet()
            seen.add(PREFERRED_PORT)
            var attemptCount = 0
            while (true) {
                portCandidate = Random.Default.nextInt(1024, 65535)
                if (seen.contains(portCandidate)) {
                    continue
                }
                attemptCount += 1
                seen.add(portCandidate)
                var lastError: Exception?
                try {
                    slobber.start("127.0.0.1", portCandidate)
                    port = portCandidate
                    break
                } catch (e1: IOException) {
                    lastError = e1
                    Log.w(TAG, "Failed to start on port $portCandidate", e1)
                }
                if (attemptCount >= 20) {
                    throw RuntimeException("Failed to start web server", lastError)
                }
            }
        }
    }

    fun prefs(): SharedPreferences = getDefaultSharedPreferences(this)

    val preferredTheme: String?
        get() = prefs().getString(PREF_UI_THEME, PREF_UI_THEME_SYSTEM)

    fun installTheme(activity: AppCompatActivity) {
        activity.delegate.localNightMode = when (preferredTheme) {
            PREF_UI_THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            PREF_UI_THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        activity.delegate.applyDayNight()
    }

    fun push(activity: AppCompatActivity) {
        articleActivities.add(activity)
        Log.d(TAG, "Activity added, stack size " + articleActivities.size)
        if (articleActivities.size > 3) {
            Log.d(TAG, "Max stack size exceeded, finishing oldest activity")
            articleActivities[0].finish()
        }
    }

    fun pop(activity: AppCompatActivity) = articleActivities.remove(activity)

    val activeSlobs: Array<Slob>
        get() = dictionaries.asSequence()
            .filter { it.active }
            .mapNotNull {slob -> slobber.getSlob(slob.id) }
            .toList()
            .toTypedArray()

    private val favoriteSlobs: Array<Slob>
        get() = dictionaries.asSequence()
            .filter { it.active && it.priority > 0 }
            .mapNotNull {slob -> slobber.getSlob(slob.id) }
            .toList()
            .toTypedArray()

    suspend fun find(key: String?): Iterator<Slob.Blob> = withContext(appScope.coroutineContext){
        Slob.find(key, *activeSlobs)
    }

    //When following links we want to consider all dictionaries
    //including the ones user turned off
    @JvmOverloads
    fun find(
        key: String?,
        preferredSlobId: String?,
        activeOnly: Boolean = false,
        upToStrength: Slob.Strength? = null
    ): PeekableIterator<Slob.Blob> {
        val t0 = System.currentTimeMillis()
        val slobs = if (activeOnly) activeSlobs else slobber.slobs
        val result = Slob.find(key, slobs, slobber.findSlob(preferredSlobId), upToStrength)
        Log.d(TAG, String.format("find ran in %dms", System.currentTimeMillis() - t0))
        return result
    }

    var isOnlyFavDictsForRandomLookup: Boolean
        get() = prefs().getBoolean(PREF_RANDOM_FAV_LOOKUP, false)
        set(value) = setBooleanPref(PREF_RANDOM_FAV_LOOKUP, value)

    var useVolumeForNav: Boolean
        get() = prefs().getBoolean(PREF_USE_VOLUME_FOR_NAV, true)
        set(value) = setBooleanPref(PREF_USE_VOLUME_FOR_NAV, value)

    var autoPaste: Boolean
        get() = prefs().getBoolean(PREF_AUTO_PASTE, false)
        set(value) = setBooleanPref(PREF_AUTO_PASTE, value)

    internal fun setBooleanPref(key: String, value: Boolean) =
        prefs().edit()
            .putBoolean(key, value)
            .apply()

    internal fun setStringPref(key: String, value: String) =
        prefs().edit()
            .putString(key, value)
            .apply()

    fun random(): Slob.Blob? {
        val slobs = if (isOnlyFavDictsForRandomLookup) favoriteSlobs else activeSlobs
        return slobber.findRandom(slobs)
    }

    fun getUrl(blob: Slob.Blob?):String {
        val base = "http://$LOCALHOST:$port"
        if (blob != null) return base + Slobber.mkContentURL(blob)
        return base
    }

    fun getSlob(slobId: String?): Slob? = slobber.getSlob(slobId)

    @Synchronized
    fun addDictionary(uri: Uri): Boolean {
        val newDesc = SlobDescriptor.fromUri(applicationContext, uri.toString())
        if (newDesc.id != null) {
            for (d in dictionaries) {
                if (d.id != null && d.id == newDesc.id) {
                    return true
                }
            }
        }
        dictionaries.add(newDesc)
        return false
    }

    fun findSlob(slobOrUri: String?): Slob? = slobber.findSlob(slobOrUri)

    fun getSlobURI(slobId: String?): String? = slobber.getSlobURI(slobId)

    fun addBookmark(contentURL: String?): BlobDescriptor? = bookmarks.add(contentURL)

    fun removeBookmark(contentURL: String?): BlobDescriptor? = bookmarks.remove(contentURL)

    fun isBookmarked(contentURL: String?): Boolean = bookmarks.contains(contentURL)

    private fun setLookupResult(query: String, data: Iterator<Slob.Blob>) {
        lastResult.setData(data)
        lookupQuery = query
        setStringPref("query", query)
    }

    private var currentLookupTask: Job? = null
    @JvmOverloads
    fun lookup(query: String?, async: Boolean = true) {
        if (currentLookupTask != null) {
            currentLookupTask!!.cancel()
            notifyLookupCanceled(query)
            currentLookupTask = null
        }
        notifyLookupStarted(query)
        if (query.isNullOrEmpty()) {
            setLookupResult("", Collections.emptyIterator())
            notifyLookupFinished(query)
            return
        }
        if (async) {
            currentLookupTask = appScope.launch {
                setLookupResult(query, find(query))
                notifyLookupFinished(query)
                currentLookupTask = null
            }
        } else suspend {
            setLookupResult(query, find(query))
            notifyLookupFinished(query)
        }
    }

    private fun notifyLookupStarted(query: String?) =
        lookupListeners.forEach { l -> l.onLookupStarted(query) }

    private fun notifyLookupFinished(query: String?) =
        lookupListeners.forEach { l -> l.onLookupFinished(query) }

    private fun notifyLookupCanceled(query: String?) =
        lookupListeners.forEach { l -> l.onLookupCanceled(query) }

    private val lookupListeners: MutableList<LookupListener> = ArrayList()
    fun addLookupListener(listener: LookupListener) = lookupListeners.add(listener)

    fun removeLookupListener(listener: LookupListener) = lookupListeners.remove(listener)

    internal class FileTooBigException : IOException()
    private suspend fun enableLinkHandling(slobs: Array<Slob>) = withContext(coroutineContext){
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
                Log.w(TAG,"Dictionary ${slob.id} (${slob.tags}) has no uri tag", ex)
            }
        }
        var t0 = System.currentTimeMillis()
        try {
            val pm = packageManager
            val p = pm.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
            )
            Log.d(
                TAG,
                "Done getting available activities in " + (System.currentTimeMillis() - t0)
            )
            t0 = System.currentTimeMillis()
            for (activityInfo in p.activities) {
                if (activityInfo.targetActivity != null) {
                    val enabled = hosts.contains(activityInfo.name)
                    if (enabled) {
                        Log.d(TAG, "Enabling links handling for " + activityInfo.name)
                    }
                    val setting =
                        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    pm.setComponentEnabledSetting(
                        ComponentName(applicationContext, activityInfo.name),
                        setting, PackageManager.DONT_KILL_APP
                    )
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, e)
        }
        Log.d(TAG, "Done enabling activities in " + (System.currentTimeMillis() - t0))
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"
        private const val PREFERRED_PORT = 8013
        @JvmField
        var jsStyleSwitcher: String? = null
        @JvmField
        var jsUserStyle: String? = null
        @JvmField
        var jsClearUserStyle: String? = null
        @JvmField
        var jsSetCannedStyle: String? = null
        const val PREF_RANDOM_FAV_LOOKUP = "onlyFavDictsForRandomLookup"
        const val PREF_UI_THEME = "UITheme"
        const val PREF_UI_THEME_LIGHT = "light"
        const val PREF_UI_THEME_DARK = "dark"
        const val PREF_UI_THEME_SYSTEM = "auto"
        const val PREF_USE_VOLUME_FOR_NAV = "useVolumeForNav"
        const val PREF_AUTO_PASTE = "autoPaste"
        private val TAG = AriApplication::class.java.simpleName
        @JvmStatic
        @Throws(IOException::class)
        fun readTextFile(`is`: InputStream?, maxSize: Int): String {
            val reader = InputStreamReader(`is`, StandardCharsets.UTF_8)
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
        fun getApp(activity: Activity?): AriApplication = activity?.application as AriApplication
    }
}