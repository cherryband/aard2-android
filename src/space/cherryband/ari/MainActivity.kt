package space.cherryband.ari

import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import android.os.Bundle
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Patterns
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentTransaction
import java.lang.Exception

class MainActivity : AppCompatActivity(), ActionBar.TabListener {
    private lateinit var appSectionsPagerAdapter: AppSectionsPagerAdapter
    private lateinit var viewPager: ViewPager
    private val NO_PASTE_PATTERNS = arrayOf(
        Patterns.WEB_URL,
        Patterns.EMAIL_ADDRESS,
        Patterns.PHONE
    )
    private lateinit var app: AriApplication
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as AriApplication
        app.installTheme(this)
        setContentView(R.layout.activity_main)
        appSectionsPagerAdapter = AppSectionsPagerAdapter(
            supportFragmentManager
        )
        val actionBar = supportActionBar!!
        actionBar.setHomeButtonEnabled(true)
        actionBar.navigationMode = ActionBar.NAVIGATION_MODE_TABS
        viewPager = findViewById(R.id.pager)
        viewPager.offscreenPageLimit = appSectionsPagerAdapter.count
        viewPager.adapter = appSectionsPagerAdapter
        val subtitles = arrayOf(
            getString(R.string.subtitle_lookup),
            getString(R.string.subtitle_bookmark),
            getString(R.string.subtitle_history),
            getString(R.string.subtitle_dictionaries),
            getString(R.string.subtitle_settings)
        )
        viewPager.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    actionBar.setSelectedNavigationItem(position)
                    actionBar.subtitle = subtitles[position]
                }
            })
        val tabIcons = arrayOfNulls<Drawable>(5)
        tabIcons[0] = IconMaker.tab(this, IconMaker.IC_SEARCH)
        tabIcons[1] = IconMaker.tab(this, IconMaker.IC_BOOKMARK)
        tabIcons[2] = IconMaker.tab(this, IconMaker.IC_HISTORY)
        tabIcons[3] = IconMaker.tab(this, IconMaker.IC_DICTIONARY)
        tabIcons[4] = IconMaker.tab(this, IconMaker.IC_SETTINGS)
        // For each of the sections in the app, add a tab to the action bar.
        for (i in 0 until appSectionsPagerAdapter.count) {
            val tab = actionBar.newTab()
            tab.setTabListener(this)
            tab.icon = tabIcons[i]
            actionBar.addTab(tab)
        }
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState)
        } else {
            if (app.dictionaries!!.size == 0) {
                viewPager.currentItem = 3
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val currentSection = savedInstanceState.getInt("currentSection")
        viewPager.currentItem = currentSection
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentSection", viewPager.currentItem)
    }

    override fun onTabUnselected(
        tab: ActionBar.Tab,
        fragmentTransaction: FragmentTransaction
    ) {
        val frag = appSectionsPagerAdapter.getItem(tab.position)
        if (frag is BaseListFragment) {
            frag.finishActionMode()
        }
        if (tab.position == 0) {
            val v = this.currentFocus
            if (v != null) {
                val mgr = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                mgr.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }
        }
    }

    override fun onTabSelected(
        tab: ActionBar.Tab,
        fragmentTransaction: FragmentTransaction
    ) {
        viewPager.currentItem = tab.position
    }

    override fun onTabReselected(
        tab: ActionBar.Tab,
        fragmentTransaction: FragmentTransaction
    ) {
    }

    override fun onPause() {
        //Looks like shown soft input sometimes causes a system ui visibility
        //change event that breaks article activity launched from here out of full screen mode.
        //Hiding it appears to reduce that.
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        try {
            inputMethodManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Hiding soft input failed", e)
        }
        super.onPause()
    }

    override fun onBackPressed() {
        val currentItem = viewPager.currentItem
        val frag = appSectionsPagerAdapter.getItem(currentItem)
        Log.d(TAG, "current tab: $currentItem")
        if (frag is BlobDescriptorListFragment) {
            if (frag.isFilterExpanded) {
                Log.d(TAG, "Filter is expanded")
                frag.collapseFilter()
                return
            }
        }
        super.onBackPressed()
    }

    internal class BookmarksFragment : BlobDescriptorListFragment() {
        override val descriptorList: BlobDescriptorList
            get() = AriApplication.getApp(activity!!).bookmarks!!
        override val itemClickAction = "showBookmarks"
        override val emptyIcon: Char = IconMaker.IC_BOOKMARK
        override val emptyText
            get() = getString(R.string.main_empty_bookmarks)
        override val deleteConfirmationItemCountResId: Int = R.plurals.confirm_delete_bookmark_count
        override val preferencesNS = "bookmarks"
    }

    internal class HistoryFragment : BlobDescriptorListFragment() {
        override val descriptorList: BlobDescriptorList
            get() = AriApplication.getApp(activity!!).history!!
        override val itemClickAction = "showHistory"
        override val emptyIcon: Char = IconMaker.IC_HISTORY
        override val emptyText
            get() = getString(R.string.main_empty_history)
        override val deleteConfirmationItemCountResId: Int = R.plurals.confirm_delete_history_count
        override val preferencesNS = "history"
    }

    internal class AppSectionsPagerAdapter(fm: FragmentManager?) : FragmentPagerAdapter(fm) {
        private val fragments: Array<Fragment> = arrayOf(
            LookupFragment(), BookmarksFragment(), HistoryFragment(),
            DictionariesFragment(), SettingsFragment()
        )

        override fun getItem(i: Int) = fragments[i]
        override fun getCount(): Int = fragments.size
        override fun getPageTitle(position: Int) = ""
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!autoPaste()) {
            Log.d(TAG, "Auto-paste is off")
            return
        }
        if (!hasFocus) {
            Log.d(TAG, "has no focus")
            return
        }
        val text = Clipboard.peek(this)
        if (text != null) {
            viewPager.currentItem = 0
            invalidateOptionsMenu()
        }
    }

    private fun useVolumeForNav(): Boolean = app.useVolumeForNav
    private fun autoPaste(): Boolean = app.autoPaste

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCanceled) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!useVolumeForNav()) {
                return false
            }
            val current = viewPager.currentItem
            if (current > 0) {
                viewPager.currentItem = current - 1
            } else {
                viewPager.currentItem = appSectionsPagerAdapter.count - 1
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false
            }
            val current = viewPager.currentItem
            if (current < appSectionsPagerAdapter.count - 1) {
                viewPager.currentItem = current + 1
            } else {
                viewPager.currentItem = 0
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            useVolumeForNav()
        } else super.onKeyDown(keyCode, event)
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}