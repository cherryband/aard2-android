package space.cherryband.ari

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import java.util.*

class LookupFragment : BaseListFragment(), LookupListener {
    private var timer: Timer? = null
    private lateinit var searchView: SearchView

    override val emptyIcon: Char = IconMaker.IC_SEARCH
    override val emptyText = ""
    override val supportsSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = activity!!.application as AriApplication
        app.addLookupListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBusy(false)
        val listView = listView
        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                Log.i("--", "Item clicked: $position")
                Intent(activity, ArticleCollectionActivity::class.java)
                    .putExtra("position", position)
                    .let { startActivity(it) }
            }
        val app = activity!!.application as AriApplication
        getListView().adapter = app.lastResult
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_random) {
            val app = activity!!.application as AriApplication
            val blob = app.random()
            if (blob == null) {
                Toast.makeText(
                    context,
                    R.string.article_collection_nothing_found,
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
            val intent = Intent(activity, ArticleCollectionActivity::class.java)
            intent.data = Uri.parse(app.getUrl(blob))
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private val closeListener = SearchView.OnCloseListener { true }
    private val queryTextListener = object : SearchView.OnQueryTextListener {
        var scheduledLookup: TimerTask? = null
        override fun onQueryTextSubmit(query: String): Boolean {
            Log.d(TAG, "query text submit: $query")
            onQueryTextChange(query)
            return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
            Log.d(TAG, "new query text: $newText")
            val app = activity!!.application as AriApplication
            val doLookup: TimerTask = object : TimerTask() {
                override fun run() {
                    val query = searchView.query.toString()
                    if (app.lookupQuery == query) { return }
                    activity!!.runOnUiThread { app.lookup(query) }
                    scheduledLookup = null
                }
            }
            val query = searchView.query.toString()
            if (app.lookupQuery != query) {
                scheduledLookup?.cancel()
                scheduledLookup = doLookup
                timer!!.schedule(doLookup, 600)
            }
            return true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        timer = Timer()
        inflater.inflate(R.menu.lookup, menu)
        val miFilter = menu.findItem(R.id.action_lookup)
        val filterActionView = miFilter.actionView
        searchView = filterActionView.findViewById(R.id.fldLookup)
        searchView.apply {
            queryHint = miFilter.title
            setOnQueryTextListener(queryTextListener)
            setOnCloseListener(closeListener)
            isSubmitButtonEnabled = false
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val activity = activity
        super.onPrepareOptionsMenu(menu)
        val app = activity!!.application as AriApplication
        if (app.autoPaste) {
            val clipboard = Clipboard.take(activity)
            if (clipboard != null) {
                app.lookup(clipboard.toString(), false)
            }
        }
        val query: CharSequence = app.lookupQuery
        searchView.setQuery(query, true)
        if (app.lastResult!!.count > 0) {
            searchView.clearFocus()
        }
        val miRandomArticle = menu.findItem(R.id.action_random)
        miRandomArticle.icon = IconMaker.actionBar(activity, IconMaker.IC_RELOAD)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val query = searchView.query.toString()
        outState.putString("lookupQuery", query)
    }

    private fun setBusy(busy: Boolean) {
        setListShown(!busy)
        if (!busy) {
            val app = activity!!.application as AriApplication
            val emptyText = emptyView.findViewById<TextView>(R.id.empty_text)
            var msg = ""
            val query = app.lookupQuery
            if (query != "") {
                msg = getString(R.string.lookup_nothing_found)
            }
            emptyText.text = msg
        }
    }

    override fun onDestroy() {
        if (timer != null) {
            timer!!.cancel()
        }
        val app = activity!!.application as AriApplication
        app.removeLookupListener(this)
        super.onDestroy()
    }

    override fun onLookupStarted(query: String) = setBusy(true)
    override fun onLookupFinished(query: String) = setBusy(false)
    override fun onLookupCanceled(query: String) = setBusy(false)

    companion object {
        private val TAG = LookupFragment::class.java.simpleName
    }
}