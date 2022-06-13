package space.cherryband.ari.ui

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import itkach.slob.Slob
import space.cherryband.ari.R
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BlobListAdapter @JvmOverloads constructor(
    context: Context,
    chunkSize: Int = 20,
    loadMoreThreshold: Int = 10
) : BaseAdapter() {
    private val mainHandler: Handler
    val list: MutableList<Slob.Blob>
    private var iterator: Iterator<Slob.Blob>? = null
    private val executor: ExecutorService
    private val chunkSize: Int
    private val loadMoreThreshold: Int
    fun setData(lookupResultsIterator: Iterator<Slob.Blob>?) {
        mainHandler.post {
            list.clear()
            notifyDataSetChanged()
        }
        iterator = lookupResultsIterator
        loadChunkSync()
    }

    private fun loadChunkSync() {
        val t0 = System.currentTimeMillis()
        var count = 0
        val chunkList: MutableList<Slob.Blob> = LinkedList()
        while (iterator!!.hasNext() && count < chunkSize && list.size <= MAX_SIZE) {
            count++
            val b = iterator!!.next()
            chunkList.add(b)
        }
        mainHandler.post {
            list.addAll(chunkList)
            notifyDataSetChanged()
        }
        Log.d(
            TAG,
            "Loaded chunk of $count (adapter size ${list.size})" +
                    " in ${System.currentTimeMillis() - t0} ms"
        )
    }

    private fun loadChunk() {
        if (!iterator!!.hasNext()) {
            return
        }
        executor.execute { loadChunkSync() }
    }

    override fun getCount(): Int = list.size

    override fun getItem(position: Int): Any {
        val result: Any = list[position]
        maybeLoadMore(position)
        return result
    }

    override fun getItemId(position: Int) = position.toLong()

    private fun maybeLoadMore(position: Int) {
        if (position >= list.size - loadMoreThreshold) {
            loadChunk()
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = list[position]
        val slob = item.owner
        maybeLoadMore(position)
        val view: View = if (convertView != null) {
            convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(R.layout.blob_descriptor_list_item, parent, false)
        }
        val titleView: TextView = view.findViewById(R.id.blob_descriptor_key)
        val sourceView: TextView = view.findViewById(R.id.blob_descriptor_source)
        val timestampView: TextView = view.findViewById(R.id.blob_descriptor_timestamp)
        titleView.text = item.key
        sourceView.text = if (slob == null) "???" else slob.tags["label"]
        timestampView.text = ""
        timestampView.visibility = View.GONE
        return view
    }

    companion object {
        private const val MAX_SIZE = 10000
        private val TAG = BlobListAdapter::class.java.simpleName
    }

    init {
        mainHandler = Handler(context.mainLooper)
        executor = Executors.newSingleThreadExecutor()
        list = ArrayList(chunkSize)
        this.chunkSize = chunkSize
        this.loadMoreThreshold = loadMoreThreshold
    }
}