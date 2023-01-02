package space.cherryband.ari.data

import android.database.DataSetObservable
import android.database.DataSetObserver
import android.util.Log
import java.util.*

abstract class BaseDescriptorList<T : BaseDescriptor> internal constructor(
    private val typeParameterClass: Class<T>?,
    private val store: DescriptorStore<T>
) : AbstractList<T>() {
    private val dataSetObservable: DataSetObservable = DataSetObservable()
    private val list: MutableList<T> = ArrayList()
    private var updating: Int = 0

    override val size: Int
        get() = list.size

    fun registerDataSetObserver(observer: DataSetObserver) {
        dataSetObservable.registerObserver(observer)
    }

    fun unregisterDataSetObserver(observer: DataSetObserver) {
        dataSetObservable.unregisterObserver(observer)
    }

    fun beginUpdate() {
        Log.d(javaClass.name, "beginUpdate")
        updating++
    }

    fun endUpdate(changed: Boolean) {
        Log.d(javaClass.name, "endUpdate, changed? $changed")
        updating--
        if (changed) {
            notifyChanged()
        }
    }

    protected fun notifyChanged() {
        if (updating == 0) {
            dataSetObservable.notifyChanged()
        }
    }

    open fun load() {
        this.addAll(store.load(typeParameterClass))
    }

    override fun get(i: Int): T {
        return list[i]
    }

    override fun set(index: Int, element: T): T {
        val result = list.set(index, element)
        store.save(element)
        notifyChanged()
        return result
    }

    override fun add(index: Int, element: T) {
        list.add(index, element)
        store.save(element)
        notifyChanged()
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        beginUpdate()
        val result = super.addAll(index, elements)
        endUpdate(result)
        return result
    }

    override fun addAll(elements: Collection<T>): Boolean {
        beginUpdate()
        val result = super.addAll(elements)
        endUpdate(result)
        return result
    }

    override fun removeAt(location: Int): T {
        val result = list.removeAt(location)
        store.delete(result.id)
        notifyChanged()
        return result
    }

    override fun clear() {
        val wasEmpty = size == 0
        beginUpdate()
        super.clear()
        endUpdate(!wasEmpty)
    }
}