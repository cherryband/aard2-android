package space.cherryband.ari

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat

internal abstract class BlobDescriptorListFragment : BaseListFragment() {
    private var icFilter: Drawable? = null
    private var icClock: Drawable? = null
    private var icList: Drawable? = null
    private var icArrowUp: Drawable? = null
    private var icArrowDown: Drawable? = null
    private var listAdapter: BlobDescriptorListAdapter? = null
    private var deleteConfirmationDialog: AlertDialog? = null
    private var miFilter: MenuItem? = null
    val isFilterExpanded: Boolean
        get() = miFilter != null && MenuItemCompat.isActionViewExpanded(miFilter)

    fun collapseFilter() {
        if (miFilter != null) {
            MenuItemCompat.collapseActionView(miFilter)
        }
    }

    abstract val descriptorList: BlobDescriptorList
    abstract val itemClickAction: String?
    override fun setSelectionMode(selectionMode: Boolean) {
        listAdapter!!.isSelectionMode = selectionMode
    }

    override val selectionMenuId: Int = R.menu.blob_descriptor_selection
    abstract val deleteConfirmationItemCountResId: Int
    abstract val preferencesNS: String?
    private fun prefs(): SharedPreferences =
        activity!!.getSharedPreferences(preferencesNS, AppCompatActivity.MODE_PRIVATE)

    override fun onSelectionActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val listView = listView
        val itemId = item!!.itemId
        if (itemId == R.id.blob_descriptor_delete) {
            val count = listView.checkedItemCount
            val countStr = resources.getQuantityString(
                deleteConfirmationItemCountResId, count, count
            )
            val message = getString(R.string.blob_descriptor_confirm_delete, countStr)
            deleteConfirmationDialog = AlertDialog.Builder(activity!!)
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
            val itemCount = listView.count
            for (i in itemCount - 1 downTo -1 + 1) {
                listView.setItemChecked(i, true)
            }
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
        listAdapter = BlobDescriptorListAdapter(descriptorList)
        icFilter = IconMaker.actionBar(activity, IconMaker.IC_FILTER)
        icClock = IconMaker.actionBar(activity, IconMaker.IC_CLOCK)
        icList = IconMaker.actionBar(activity, IconMaker.IC_LIST)
        icArrowUp = IconMaker.actionBar(activity, IconMaker.IC_SORT_ASC)
        icArrowDown = IconMaker.actionBar(activity, IconMaker.IC_SORT_DESC)
        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                Intent(
                    activity,
                    ArticleCollectionActivity::class.java
                ).apply {
                    action = itemClickAction
                    putExtra("position", position)
                }.let { startActivity(it) }
            }
        setListAdapter(listAdapter)
    }

    private fun deleteSelectedItems() {
        val checkedItems = listView.checkedItemPositions
        for (i in checkedItems.size() - 1 downTo -1 + 1) {
            val position = checkedItems.keyAt(i)
            val checked = checkedItems[position]
            if (checked) {
                descriptorList.removeAt(position)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.blob_descriptor_list, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val list = descriptorList
        miFilter = menu.findItem(R.id.action_filter)
        miFilter?.icon = icFilter
        val filterActionView = MenuItemCompat.getActionView(miFilter)
        val searchView = filterActionView
            .findViewById<View>(R.id.fldFilter) as SearchView
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
        super.onPrepareOptionsMenu(menu)
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

    override fun onOptionsItemSelected(mi: MenuItem): Boolean {
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
        return super.onOptionsItemSelected(mi)
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