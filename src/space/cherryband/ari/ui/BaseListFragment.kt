package space.cherryband.ari.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.AbsListView.MultiChoiceModeListener
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.ListFragment
import space.cherryband.ari.R

abstract class BaseListFragment : ListFragment() {
    protected lateinit var emptyView: View
    var actionMode: ActionMode? = null

    abstract val emptyIcon: Char
    abstract val emptyText: CharSequence?
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        emptyView = inflater.inflate(R.layout.empty_view, container, false)
        emptyView.findViewById<TextView>(R.id.empty_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = emptyText
        }
        emptyView.findViewById<ImageView>(R.id.empty_icon).apply {
            setImageDrawable(IconMaker.emptyView(activity, emptyIcon))
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    protected open fun setSelectionMode(selectionMode: Boolean) {}
    protected open val selectionMenuId = 0

    protected open fun onSelectionActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
    protected open val supportsSelection = true

    fun finishActionMode(): Boolean {
        if (actionMode != null) {
            actionMode!!.finish()
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.emptyView = emptyView
        (listView.parent as ViewGroup).addView(emptyView, 0)
        if (supportsSelection) {
            listView.itemsCanFocus = false
            listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            listView.setMultiChoiceModeListener(object : MultiChoiceModeListener {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    actionMode = mode
                    val inflater = mode.menuInflater
                    inflater.inflate(selectionMenuId, menu)
                    val miDelete = menu.findItem(R.id.blob_descriptor_delete)
                    if (miDelete != null) {
                        miDelete.icon = IconMaker.actionBar(activity, IconMaker.IC_TRASH)
                    }
                    val miSelectAll = menu.findItem(R.id.blob_descriptor_select_all)
                    if (miSelectAll != null) {
                        miSelectAll.icon = IconMaker.actionBar(activity, IconMaker.IC_SELECT_ALL)
                    }
                    setSelectionMode(true)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean
                = onSelectionActionItemClicked(mode, item)

                override fun onDestroyActionMode(mode: ActionMode) {
                    setSelectionMode(false)
                    actionMode = null
                }

                override fun onItemCheckedStateChanged(
                    mode: ActionMode,
                    position: Int, id: Long, checked: Boolean
                ) {
                }
            })
        }
    }
}