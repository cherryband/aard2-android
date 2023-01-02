package space.cherryband.ari.data

open class BaseDescriptor {
    @JvmField
    var id: String? = null
    @JvmField
    var createdAt: Long = 0
    @JvmField
    var lastAccess: Long = 0
}