package space.cherryband.ari.data

import android.database.DataSetObservable
import android.database.DataSetObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ibm.icu.text.Collator
import com.ibm.icu.text.RuleBasedCollator
import com.ibm.icu.text.StringSearch
import itkach.slob.Slob
import itkach.slob.Slob.KeyComparator
import space.cherryband.ari.AriApplication
import space.cherryband.ari.util.Util.safeSort
import java.text.StringCharacterIterator
import java.util.*

class BlobDescriptorList @JvmOverloads constructor(
    private val app: AriApplication,
    private val store: DescriptorStore<BlobDescriptor>,
    private val maxSize: Int = 100
) : AbstractList<BlobDescriptor?>() {

    enum class SortOrder {
        TIME, NAME
    }

    private val list: MutableList<BlobDescriptor?> = ArrayList()
    private val filteredList: MutableList<BlobDescriptor?> = ArrayList()
    var filter: String? = ""
        set (value) {
            field = value
            notifyDataSetChanged()
        }

    var sortOrder: SortOrder = SortOrder.TIME
        private set
    var isAscending: Boolean = false
        private set
    private var comparator: Comparator<BlobDescriptor?>? = null
    private val dataSetObservable: DataSetObservable = DataSetObservable()
    private val nameComparatorAsc: Comparator<BlobDescriptor?>
    = Comparator { b1, b2 -> keyComparator.compare(b1!!.key, b2!!.key) }
    private val nameComparatorDesc: Comparator<BlobDescriptor?> = Collections.reverseOrder(nameComparatorAsc)
    private val timeComparatorAsc: Comparator<BlobDescriptor?>
    = Comparator { b1, b2 -> b1!!.createdAt compareTo b2!!.createdAt }
    private val timeComparatorDesc: Comparator<BlobDescriptor?> = Collections.reverseOrder(timeComparatorAsc)
    private val lastAccessComparator: Comparator<BlobDescriptor?>
    = Comparator { b1, b2 -> b2!!.lastAccess compareTo b1!!.lastAccess }
    private val keyComparator: KeyComparator = Slob.Strength.QUATERNARY.comparator
    private val handler: Handler = Handler(Looper.getMainLooper())

    private var filterCollator: RuleBasedCollator?
    = (Collator.getInstance(Locale.ROOT).clone() as RuleBasedCollator).apply {
        strength = Collator.PRIMARY
        isAlternateHandlingShifted = false
    }

    init {
        setSort(sortOrder, false)
    }

    fun registerDataSetObserver(observer: DataSetObserver) = dataSetObservable.registerObserver(observer)

    fun unregisterDataSetObserver(observer: DataSetObserver) = dataSetObservable.unregisterObserver(observer)

    /**
     * Notifies the attached observers that the underlying data has been changed
     * and any View reflecting the data set should refresh itself.
     */
    fun notifyDataSetChanged() {
        filteredList.clear()
        if (filter.isNullOrEmpty()) {
            filteredList.addAll(list)
        } else {
            for (bd in list) {
                val stringSearch = StringSearch(
                    filter, StringCharacterIterator(bd!!.key), filterCollator
                )
                val matchPos = stringSearch.first()
                if (matchPos != StringSearch.DONE) {
                    filteredList.add(bd)
                }
            }
        }
        sortOrderChanged()
    }

    private fun sortOrderChanged() {
        filteredList.safeSort(comparator)
        dataSetObservable.notifyChanged()
    }

    /**
     * Notifies the attached observers that the underlying data is no longer
     * valid or available. Once invoked this adapter is no longer valid and
     * should not report further data set changes.
     */
    fun notifyDataSetInvalidated() = dataSetObservable.notifyInvalidated()

    fun load() {
        list.addAll(store.load(BlobDescriptor::class.java))
        notifyDataSetChanged()
    }

    private fun doUpdateLastAccess(bd: BlobDescriptor) {
        val t = System.currentTimeMillis()
        val dt = t - bd.lastAccess
        if (dt < 2000) {
            return
        }
        bd.lastAccess = t
        store.save(bd)
    }

    private fun updateLastAccess(bd: BlobDescriptor) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doUpdateLastAccess(bd)
        } else {
            handler.post { doUpdateLastAccess(bd) }
        }
    }

    fun resolveOwner(bd: BlobDescriptor): Slob? {
        var slob = app.getSlob(bd.slobId)
        //        if (slob == null || !slob.file.exists()) {
        if (slob == null) {
            slob = app.findSlob(bd.slobUri)
        }
        return slob
    }

    fun resolve(bd: BlobDescriptor): Slob.Blob? {
        val slob = resolveOwner(bd)
        var blob: Slob.Blob? = null
        if (slob == null) {
            return null
        }
        val slobId = slob.id.toString()
        if (slobId == bd.slobId) {
            blob = Slob.Blob(slob, bd.blobId, bd.key, bd.fragment)
        } else {
            try {
                val result = slob.find(
                    bd.key,
                    Slob.Strength.QUATERNARY
                )
                if (result.hasNext()) {
                    blob = result.next()
                    bd.slobId = slobId
                    bd.blobId = blob.id
                }
            } catch (ex: Exception) {
                Log.w(
                    TAG, String.format(
                        "Failed to resolve descriptor %s (%s) in %s (%s)",
                        bd.blobId, bd.key, slob.id, slob.fileURI
                    ), ex
                )
                blob = null
            }
        }
        if (blob != null) {
            updateLastAccess(bd)
        }
        return blob
    }

    private fun createDescriptor(contentUrl: String?): BlobDescriptor? {
        Log.d(TAG, "Create descriptor from content url: $contentUrl")
        val uri = contentUrl?.let { Uri.parse(it) }
        val bd: BlobDescriptor? = uri?.let { BlobDescriptor.fromUri(it) }
        if (bd != null) {
            bd.slobUri = app.getSlobURI(bd.slobId)
        }
        return bd
    }

    fun add(contentUrl: String?): BlobDescriptor? {
        val bd = createDescriptor(contentUrl)
        val index = list.indexOf(bd)
        if (index > -1) {
            return list[index]
        }
        list.add(bd)
        store.save(bd!!)
        if (list.size > maxSize) {
            list.safeSort(lastAccessComparator)
            val lru = list.removeAt(list.size - 1)
            store.delete(lru!!.id)
        }
        notifyDataSetChanged()
        return bd
    }

    fun remove(contentUrl: String?): BlobDescriptor? {
        val index = list.indexOf(createDescriptor(contentUrl))
        return if (index > -1) {
            removeByIndex(index)
        } else null
    }

    override fun removeAt(index: Int): BlobDescriptor? {
        //FIXME find exact item by uuid or using sorted<->unsorted mapping
        val bd = filteredList[index]
        val realIndex = list.indexOf(bd)
        return if (realIndex > -1) {
            removeByIndex(realIndex)
        } else null
    }

    private fun removeByIndex(index: Int): BlobDescriptor? {
        val bd = list.removeAt(index)
        if (bd != null) {
            val removed = store.delete(bd.id)
            Log.d(TAG, String.format("Item (%s) %s removed? %s", bd.key, bd.id, removed))
            if (removed) {
                notifyDataSetChanged()
            }
        }
        return bd
    }

    operator fun contains(contentUrl: String?): Boolean {
        val toFind = createDescriptor(contentUrl)
        for (bd in list) {
            if (bd!!.equals(toFind)) {
                Log.d(TAG, "Found exact match, bookmarked")
                return true
            }
            if (bd.key.equals(toFind!!.key) && bd.slobUri.equals(toFind.slobUri)) {
                Log.d(TAG, "Found approximate match, bookmarked")
                return true
            }
        }
        Log.d(TAG, "not bookmarked")
        return false
    }

    override fun get(index: Int): BlobDescriptor? = filteredList[index]

    override val size
        get() = filteredList.size

    @JvmOverloads
    fun setSort(order: SortOrder = sortOrder, ascending: Boolean = isAscending) {
        sortOrder = order
        isAscending = ascending
        var c: Comparator<BlobDescriptor?>? = null
        if (order == SortOrder.NAME) {
            c = if (ascending) nameComparatorAsc else nameComparatorDesc
        }
        if (order == SortOrder.TIME) {
            c = if (ascending) timeComparatorAsc else timeComparatorDesc
        }
        if (c !== comparator) {
            comparator = c
            sortOrderChanged()
        }
    }
    companion object {
        val TAG: String? = BlobDescriptor::class.java.simpleName
    }
}