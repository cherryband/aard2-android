package space.cherryband.ari.data

import itkach.slob.Slob
import space.cherryband.ari.AriApplication
import space.cherryband.ari.util.Util.safeSort

class SlobDescriptorList(
    private val app: AriApplication,
    store: DescriptorStore<SlobDescriptor>?
) : BaseDescriptorList<SlobDescriptor>(
    SlobDescriptor::class.java, store!!
) {
    private val comparator: Comparator<SlobDescriptor> = Comparator { d1: SlobDescriptor, d2: SlobDescriptor ->
        //Dictionaries that are unfavorited
        //go immediately after favorites
        if (d1.priority == 0L && d2.priority == 0L) {
            return@Comparator d2.lastAccess compareTo d1.lastAccess
        }
        //Favorites are always above other
        if (d1.priority == 0L && d2.priority > 0) {
            return@Comparator 1
        }
        if (d1.priority > 0 && d2.priority == 0L) {
            return@Comparator -1
        }
        d1.priority compareTo d2.priority
    }

    fun resolve(sd: SlobDescriptor): Slob? {
        return app.getSlob(sd.id)
    }

    fun sort() {
        safeSort(comparator)
    }

    override fun load() {
        beginUpdate()
        super.load()
        sort()
        endUpdate(true)
    }
}