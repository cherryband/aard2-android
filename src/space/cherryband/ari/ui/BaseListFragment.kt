package space.cherryband.ari.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import space.cherryband.ari.R
import space.cherryband.ari.util.IconMaker

abstract class BaseListFragment : Fragment() {
    protected lateinit var emptyView: View
    protected lateinit var listView: RecyclerView
    var actionMode: ActionMode? = null
    
    abstract val emptyIcon: Char
    abstract val emptyText: CharSequence?
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        listView = view.findViewById(R.id.base_list)
        emptyView = view.findViewById(R.id.empty_view)
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

    protected val actionModeCallback = object : ActionMode.Callback {
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
    }
}