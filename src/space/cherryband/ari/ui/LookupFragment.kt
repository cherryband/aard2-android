package space.cherryband.ari.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import space.cherryband.ari.*
import space.cherryband.ari.util.Clipboard
import space.cherryband.ari.util.IconMaker
import java.util.*

class LookupFragment : BaseListFragment(),
    LookupListener, MenuProvider {
    private var timer: Timer? = null
    private var searchView: SearchView? = null

    override val emptyIcon: Char = IconMaker.IC_SEARCH
    override val emptyText = ""
    override val supportsSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = requireActivity().application as AriApplication
        app.addLookupListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBusy(false)
        val app = requireActivity().application as AriApplication
        listView.adapter = app.lastResult
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
                    val query = searchView?.query.toString()
                    if (app.lookupQuery == query) { return }
                    requireActivity().runOnUiThread { app.lookup(query) }
                    scheduledLookup = null
                }
            }
            val query = searchView?.query.toString()
            if (app.lookupQuery != query) {
                scheduledLookup?.cancel()
                scheduledLookup = doLookup
                timer!!.schedule(doLookup, 600)
            }
            return true
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        timer = Timer()
        inflater.inflate(R.menu.lookup, menu)
        val miFilter = menu.findItem(R.id.action_lookup)
        val filterActionView = miFilter.actionView
        searchView = filterActionView.findViewById(R.id.fldLookup)
        searchView?.apply {
            queryHint = miFilter.title
            setOnQueryTextListener(queryTextListener)
            setOnCloseListener(closeListener)
            isSubmitButtonEnabled = false
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

    override fun onPrepareMenu(menu: Menu) {
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
        searchView?.setQuery(query, true)
        if (app.lastResult.itemCount > 0) {
            searchView?.clearFocus()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val query = searchView?.query.toString()
        outState.putString("lookupQuery", query)
    }

    private fun setBusy(busy: Boolean) {
        listView.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        if (!busy) {
            val app = requireActivity().application as AriApplication
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
        val app = requireActivity().application as AriApplication
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