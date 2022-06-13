package space.cherryband.ari.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import itkach.slob.Slob
import space.cherryband.ari.R
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BlobListAdapter @JvmOverloads constructor(
    context: Context,
    chunkSize: Int = 20,
    loadMoreThreashold: Int = 10
) : RecyclerView.Adapter<BlobViewHolder>() {
    private val mainHandler: Handler
    val list: MutableList<Slob.Blob>
    var iter: Iterator<Slob.Blob>? = null
    private val executor: ExecutorService
    private val chunkSize: Int
    private val loadMoreThreashold: Int

    fun setData(lookupResultsIter: Iterator<Slob.Blob>?) {
        mainHandler.post {
            list.clear()
            notifyDataSetChanged()
        }
        iter = lookupResultsIter
        loadChunkSync()
    }

    private fun loadChunkSync() {
        val t0 = System.currentTimeMillis()
        var count = 0
        val chunkList: MutableList<Slob.Blob> = LinkedList()
        val initSize = list.size
        while (iter!!.hasNext() && count < chunkSize && initSize <= MAX_SIZE) {
            count++
            val b = iter!!.next()
            chunkList.add(b)
        }
        mainHandler.post {
            list.addAll(chunkList)
            notifyItemRangeInserted(initSize, initSize + count - 1)
        }
        Log.d(
            TAG, String.format(
                "Loaded chunk of %d (adapter size %d) in %d ms",
                count, list.size, System.currentTimeMillis() - t0
            )
        )
    }

    private fun loadChunk() {
        if (!iter!!.hasNext()) {
            return
        }
        executor.execute { loadChunkSync() }
    }

    override fun getItemCount(): Int = list.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun maybeLoadMore(position: Int) {
        if (position >= list.size - loadMoreThreashold) {
            loadChunk()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlobViewHolder {
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.blob_descriptor_list_item, parent, false)
        return BlobViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlobViewHolder, position: Int) {
        val item = list[position]
        val slob = item.owner
        maybeLoadMore(position)
        holder.apply {
            titleView.text = item.key
            sourceView.text = if (slob == null) "???" else slob.tags["label"]
            timestampView.text = ""
            timestampView.visibility = View.GONE
        }
    }

    companion object {
        private val TAG = BlobListAdapter::class.java.simpleName
        private const val MAX_SIZE = 10000
    }

    init {
        mainHandler = Handler(context.mainLooper)
        executor = Executors.newSingleThreadExecutor()
        list = ArrayList(chunkSize)
        this.chunkSize = chunkSize
        this.loadMoreThreashold = loadMoreThreashold
    }
}

data class BlobViewHolder(val itemView: View): RecyclerView.ViewHolder(itemView) {
    val titleView: TextView = itemView.findViewById(R.id.blob_descriptor_key)
    val sourceView: TextView = itemView.findViewById(R.id.blob_descriptor_source)
    val timestampView: TextView = itemView.findViewById(R.id.blob_descriptor_timestamp)
    init {
        itemView.setOnClickListener { view ->
            Log.i("--", "Item clicked: $absoluteAdapterPosition")
            Intent(view.context, ArticleCollectionActivity::class.java)
                .putExtra("position", absoluteAdapterPosition)
                .let { startActivity(view.context, it, null) }
        }
    }
}
