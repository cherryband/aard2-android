package space.cherryband.ari.ui

import android.content.Context
import android.database.DataSetObserver
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import space.cherryband.ari.R
import space.cherryband.ari.data.BlobDescriptorList

class BlobDescriptorListAdapter(val list: BlobDescriptorList) : BaseAdapter() {
    private val observer: DataSetObserver
    var isSelectionMode = false
        set(selectionMode) {
            field = selectionMode
            notifyDataSetChanged()
        }

    override fun getCount(): Int = synchronized(list) { list.size }

    override fun getItem(position: Int): Any = synchronized(list) { list[position]!! }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = list[position]
        val timestamp = DateUtils.getRelativeTimeSpanString(item!!.createdAt)
        val slob = list.resolveOwner(item)
        val view: View = if (convertView != null) {
            convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(
                R.layout.blob_descriptor_list_item, parent,
                false
            )
        }
        val titleView = view.findViewById<TextView>(R.id.blob_descriptor_key)
        val sourceView = view.findViewById<TextView>(R.id.blob_descriptor_source)
        val timestampView = view.findViewById<TextView>(R.id.blob_descriptor_timestamp)
        val cb = view.findViewById<CheckBox>(R.id.blob_descriptor_checkbox)
        titleView.text = item.key
        sourceView.text = if (slob == null) "???" else slob.tags["label"]
        timestampView.text = timestamp
        cb.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        return view
    }

    init {
        observer = object : DataSetObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onInvalidated() {
                notifyDataSetInvalidated()
            }
        }
        list.registerDataSetObserver(observer)
    }
}