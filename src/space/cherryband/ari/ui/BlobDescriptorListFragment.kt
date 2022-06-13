package space.cherryband.ari.ui

import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import space.cherryband.ari.R
import space.cherryband.ari.data.BlobDescriptorList
import space.cherryband.ari.util.IconMaker

internal abstract class BlobDescriptorListFragment : BaseListFragment(), MenuProvider {
    private var icFilter: Drawable? = null
    private var icClock: Drawable? = null
    private var icList: Drawable? = null
    private var icArrowUp: Drawable? = null
    private var icArrowDown: Drawable? = null
    private var listAdapter: BlobDescriptorListAdapter? = null
    private var deleteConfirmationDialog: AlertDialog? = null
    private var miFilter: MenuItem? = null
    val isFilterExpanded: Boolean
        get() = miFilter != null && miFilter!!.isActionViewExpanded

    fun collapseFilter() {
        if (miFilter != null) {
            miFilter!!.collapseActionView()
        }
    }

    abstract val descriptorList: BlobDescriptorList
    abstract val itemClickAction: String?
    private lateinit var selectionTracker: SelectionTracker<String>
    override fun setSelectionMode(selectionMode: Boolean) {
        listAdapter!!.isSelectionMode = selectionMode
    }

    override val selectionMenuId: Int = R.menu.blob_descriptor_selection
    abstract val deleteConfirmationItemCountResId: Int
    abstract val preferencesNS: String?
    private fun prefs(): SharedPreferences =
        requireActivity().getSharedPreferences(preferencesNS, AppCompatActivity.MODE_PRIVATE)

    override fun onSelectionActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val itemId = item!!.itemId
        if (itemId == R.id.blob_descriptor_delete) {
            val count = selectionTracker.selection.size()
            val countStr = resources.getQuantityString(
                deleteConfirmationItemCountResId, count, count
            )
            val message = getString(R.string.blob_descriptor_confirm_delete, countStr)
            deleteConfirmationDialog = AlertDialog.Builder(requireActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    deleteSelectedItems()
                    mode!!.finish()
                    deleteConfirmationDialog = null
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener { deleteConfirmationDialog = null }
                .create().also { it.show() }
            return true
        } else if (itemId == R.id.blob_descriptor_select_all) {
            descriptorList.map { it.blobId }.forEach(selectionTracker::select)
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val descriptorList = descriptorList
        val p = prefs()
        val sortOrderStr = p.getString(
            PREF_SORT_ORDER,
            BlobDescriptorList.SortOrder.TIME.name
        )
        val sortOrder = BlobDescriptorList.SortOrder.valueOf(sortOrderStr!!)
        val sortDir = p.getBoolean(PREF_SORT_DIRECTION, false)
        descriptorList.setSort(sortOrder, sortDir)
        listAdapter = BlobDescriptorListAdapter(descriptorList, itemClickAction)
        icFilter = IconMaker.actionBar(activity, IconMaker.IC_FILTER)
        icClock = IconMaker.actionBar(activity, IconMaker.IC_CLOCK)
        icList = IconMaker.actionBar(activity, IconMaker.IC_LIST)
        icArrowUp = IconMaker.actionBar(activity, IconMaker.IC_SORT_ASC)
        icArrowDown = IconMaker.actionBar(activity, IconMaker.IC_SORT_DESC)
        listView.adapter = listAdapter

        selectionTracker = SelectionTracker.Builder(
            "my-selection-id",
            listView,
            BlobDescriptorListKeyProvider(descriptorList),
            BlobDescriptorDetailsLookup(listView),
            StorageStrategy.createStringStorage()
        ).build()

        selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<String>(){
            override fun onSelectionRefresh() {
                super.onSelectionRefresh()

            }

            override fun onSelectionRestored() {
                super.onSelectionRestored()
                if (!selectionTracker.selection.isEmpty) {
                    requireActivity().startActionMode(actionModeCallback)
                }
            }
        })
    }

    private fun deleteSelectedItems() {
        val checkedItems = selectionTracker.selection
        checkedItems.forEach {
            descriptorList.remove(it)
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.blob_descriptor_list, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        val list = descriptorList
        miFilter = menu.findItem(R.id.action_filter)
        miFilter?.icon = icFilter
        val filterActionView = miFilter?.actionView
        val searchView = filterActionView
            ?.findViewById<View>(R.id.fldFilter) as SearchView
        searchView.queryHint = miFilter?.title
        searchView.setQuery(list.filter, true)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = true
            override fun onQueryTextChange(newText: String): Boolean {
                if (newText != descriptorList.filter) {
                    descriptorList.filter = newText
                }
                return true
            }
        })
        setSortOrder(menu.findItem(R.id.action_sort_order), list.sortOrder)
        setAscending(menu.findItem(R.id.action_sort_asc), list.isAscending)
        super.onPrepareMenu(menu)
    }

    private fun setSortOrder(mi: MenuItem, order: BlobDescriptorList.SortOrder) {
        val icon: Drawable?
        val textRes: Int
        if (order == BlobDescriptorList.SortOrder.TIME) {
            icon = icClock
            textRes = R.string.action_sort_by_time
        } else {
            icon = icList
            textRes = R.string.action_sort_by_title
        }
        mi.icon = icon
        mi.setTitle(textRes)
        prefs().edit()
            .putString(PREF_SORT_ORDER, order.name)
            .apply()
    }

    private fun setAscending(mi: MenuItem, ascending: Boolean) {
        val icon: Drawable?
        val textRes: Int
        if (ascending) {
            icon = icArrowUp
            textRes = R.string.action_ascending
        } else {
            icon = icArrowDown
            textRes = R.string.action_descending
        }
        mi.icon = icon
        mi.setTitle(textRes)
        prefs().edit()
            .putBoolean(PREF_SORT_DIRECTION, ascending)
            .apply()
    }

    override fun onMenuItemSelected(mi: MenuItem): Boolean {
        val list = descriptorList
        val itemId = mi.itemId
        if (itemId == R.id.action_sort_asc) {
            list.setSort(!list.isAscending)
            setAscending(mi, list.isAscending)
            return true
        }
        if (itemId == R.id.action_sort_order) {
            if (list.sortOrder == BlobDescriptorList.SortOrder.TIME) {
                list.setSort(BlobDescriptorList.SortOrder.NAME)
            } else {
                list.setSort(BlobDescriptorList.SortOrder.TIME)
            }
            setSortOrder(mi, list.sortOrder)
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        deleteConfirmationDialog?.dismiss()
    }

    companion object {
        private const val PREF_SORT_ORDER = "sortOrder"
        private const val PREF_SORT_DIRECTION = "sortDir"
    }
}