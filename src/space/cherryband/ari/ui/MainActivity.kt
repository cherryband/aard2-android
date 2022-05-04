package space.cherryband.ari.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import space.cherryband.ari.*
import space.cherryband.ari.data.BlobDescriptorList
import space.cherryband.ari.util.Clipboard

class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener{
    private lateinit var appPageAdapter: AppPageAdapter
    private lateinit var viewPager: ViewPager2
    private lateinit var navBar: BottomNavigationView
    private lateinit var floatingActionButton: FloatingActionButton
    private val NO_PASTE_PATTERNS = arrayOf(
        Patterns.WEB_URL,
        Patterns.EMAIL_ADDRESS,
        Patterns.PHONE
    )
    private lateinit var app: AriApplication
    private var initTab: Int = 2
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as AriApplication
        app.installTheme(this)

        setContentView(R.layout.activity_main)
        supportActionBar?.setHomeButtonEnabled(true)

        appPageAdapter = AppPageAdapter(this)
        viewPager = findViewById(R.id.pager)
        floatingActionButton = findViewById(R.id.fab_main)
        navBar = findViewById(R.id.bottom_navigation)

        prepViewPager()
        prepFloatingActionButton()
        prepNavBar()

        app.prefs()
            .registerOnSharedPreferenceChangeListener { _, key: String ->
                if (key == AriApplication.PREF_UI_THEME) {
                    recreate()
                }
            }

        if (app.dictionaries.size == 0) {
            initTab = 3
        }
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState)
        }
        switchNavBar(initTab) // prevents initial flickering of navBar due to changing position
    }

    private fun prepFloatingActionButton() {
        floatingActionButton.setOnClickListener {
            if (viewPager.currentItem == 2) {
                val blob = app.random()
                if (blob == null) {
                    Toast.makeText(
                        this,
                        R.string.article_collection_nothing_found,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                val intent = Intent(this, ArticleCollectionActivity::class.java)
                intent.data = Uri.parse(app.getUrl(blob))
                startActivity(intent)
            }
        }
    }

    private fun prepViewPager() {
        viewPager.offscreenPageLimit = appPageAdapter.itemCount
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                switchNavBar(position)
            }
        })
        viewPager.doOnAttach {
            viewPager.adapter = appPageAdapter
            switchTab(initTab, false)
        }
    }

    private fun switchNavBar(position: Int) {
        navBar.selectedItemId = when (position) {
            0 -> R.id.tab_history
            1 -> R.id.tab_bookmarks
            2 -> R.id.tab_lookup
            3 -> R.id.tab_dictionaries
            4 -> R.id.tab_settings
            else -> R.id.tab_dictionaries
        }
    }

    private fun prepNavBar() {
        navBar.setOnItemSelectedListener(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val currentSection = savedInstanceState.getInt("currentSection")
        initTab = currentSection
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentSection", viewPager.currentItem)
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

        val frag = appPageAdapter.getItem(currentItem)
        Log.d(TAG, "current tab: $currentItem")
        if (frag is BlobDescriptorListFragment && frag.isFilterExpanded) {
            Log.d(TAG, "Filter is expanded")
            frag.collapseFilter()
            return
        }
        super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val to = when(item.itemId){
            R.id.tab_history -> 0
            R.id.tab_bookmarks ->  1
            R.id.tab_lookup ->  2
            R.id.tab_dictionaries -> 3
            R.id.tab_settings -> 4
            else -> {
                3
            }
        }
        switchTab(to)
        return true
    }

    private fun switchTab(to: Int, smoothScroll: Boolean = true) {
        val from = viewPager.currentItem
        viewPager.setCurrentItem(to, smoothScroll)
        if (viewPager.currentItem == 2) {
            floatingActionButton.show()
        } else {
            floatingActionButton.hide()
        }

        val frag = appPageAdapter.getItem(from)
        if (frag is BaseListFragment) {
            frag.finishActionMode()
        }
        if (from == 2) {
            val v = this.currentFocus
            if (v != null) {
                val mgr = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                mgr.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }
        }
    }

    internal class BookmarksFragment : BlobDescriptorListFragment() {
        override val descriptorList: BlobDescriptorList
            get() = AriApplication.getApp(requireActivity()).bookmarks
        override val itemClickAction = "showBookmarks"
        override val emptyIcon: Char = IconMaker.IC_BOOKMARK
        override val emptyText
            get() = getString(R.string.main_empty_bookmarks)
        override val deleteConfirmationItemCountResId: Int = R.plurals.confirm_delete_bookmark_count
        override val preferencesNS = "bookmarks"
    }

    internal class HistoryFragment : BlobDescriptorListFragment() {
        override val descriptorList: BlobDescriptorList
            get() = AriApplication.getApp(requireActivity()).history
        override val itemClickAction = "showHistory"
        override val emptyIcon: Char = IconMaker.IC_HISTORY
        override val emptyText
            get() = getString(R.string.main_empty_history)
        override val deleteConfirmationItemCountResId: Int = R.plurals.confirm_delete_history_count
        override val preferencesNS = "history"
    }

    internal class AppPageAdapter(fa: FragmentActivity): FragmentStateAdapter(fa) {
        private val fragments: Array<Fragment> = arrayOf(
            HistoryFragment(), BookmarksFragment(), LookupFragment(),
            DictionariesFragment(), SettingsFragment()
        )

        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment {
            return fragments[position]
        }
        internal fun getItem(position: Int) = fragments[position]
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
            viewPager.currentItem = 2
            invalidateOptionsMenu()
        }
    }

    private fun useVolumeForNav(): Boolean = app.useVolumeForNav
    private fun autoPaste(): Boolean = app.autoPaste

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCanceled) { return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!useVolumeForNav()) { return false }

            val current = viewPager.currentItem
            if (current > 0) {
                viewPager.currentItem = current - 1
            } else {
                viewPager.currentItem = appPageAdapter.itemCount - 1
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) { return false }

            val current = viewPager.currentItem
            if (current < appPageAdapter.itemCount - 1) {
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