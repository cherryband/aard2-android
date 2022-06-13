package space.cherryband.ari.ui

import android.content.Context
import android.content.Intent
import space.cherryband.ari.data.BlobDescriptorList
import android.database.DataSetObserver
import android.text.format.DateUtils
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import space.cherryband.ari.R
import android.widget.TextView
import android.widget.CheckBox
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.widget.RecyclerView

class BlobDescriptorListAdapter(val list: BlobDescriptorList, private val itemClickAction: String?)
    : RecyclerView.Adapter<BlobDescriptorViewHolder>() {
    private val observer: DataSetObserver
    var isSelectionMode = false
        set(selectionMode) {
            field = selectionMode
            notifyDataSetChanged()
        }

    init {
        observer = object : DataSetObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onInvalidated() {
                notifyDataSetChanged()
            }
        }
        list.registerDataSetObserver(observer)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlobDescriptorViewHolder {
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(
            R.layout.blob_descriptor_list_item, parent,
            false
        )
        return BlobDescriptorViewHolder(view, itemClickAction)
    }

    override fun onBindViewHolder(holder: BlobDescriptorViewHolder, position: Int) {
        val item = list[position]
        val timestamp = DateUtils.getRelativeTimeSpanString(item.createdAt)
        val slob = list.resolveOwner(item)
        holder.apply {
            titleView.text = item.key
            sourceView.text = if (slob == null) "???" else slob.tags["label"]
            timestampView.text = timestamp
            checkBoxView.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            itemDetails = BlobDescriptorItemDetails(position, item.slobUri)
        }
    }

    override fun getItemCount(): Int {
        synchronized(list) { return list.size }
    }
}

class BlobDescriptorListKeyProvider(private val list: BlobDescriptorList)
    :ItemKeyProvider<String>(SCOPE_MAPPED) {
    override fun getKey(position: Int): String? = list[position].slobUri
    override fun getPosition(key: String): Int = list.indexOfFirst { it.key == key }
}


class BlobDescriptorViewHolder(itemView: View, itemClickAction: String?)
    : RecyclerView.ViewHolder(itemView) {
    val titleView: TextView = itemView.findViewById(R.id.blob_descriptor_key)
    val sourceView: TextView = itemView.findViewById(R.id.blob_descriptor_source)
    val timestampView: TextView = itemView.findViewById(R.id.blob_descriptor_timestamp)
    val checkBoxView: CheckBox = itemView.findViewById(R.id.blob_descriptor_checkbox)
    lateinit var itemDetails: BlobDescriptorItemDetails

    init {
        itemView.setOnClickListener { view ->
            Intent(view.context,
                ArticleCollectionActivity::class.java
            ).apply {
                action = itemClickAction
                putExtra("position", absoluteAdapterPosition)
            }.let { startActivity(view.context, it, null) }
        }
    }
}

data class BlobDescriptorItemDetails(private val position: Int, private val key: String)
    : ItemDetailsLookup.ItemDetails<String>() {
    override fun getSelectionKey(): String = key
    override fun getPosition(): Int = position

}

class BlobDescriptorDetailsLookup(private val listView: RecyclerView)
    : ItemDetailsLookup<String>() {
    override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
        val view: View? = listView.findChildViewUnder(e.x, e.y)
        if (view != null) {
            val holder: RecyclerView.ViewHolder = listView.getChildViewHolder(view)
            if (holder is BlobDescriptorViewHolder) {
                return holder.itemDetails
            }
        }
        return null
    }
}