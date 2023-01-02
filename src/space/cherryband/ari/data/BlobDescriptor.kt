package space.cherryband.ari.data

import android.net.Uri
import java.util.*

class BlobDescriptor : BaseDescriptor() {
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (blobId == null) 0 else blobId.hashCode()
        result = (prime * result
                + if (fragment == null) 0 else fragment.hashCode())
        result = prime * result + if (slobId == null) 0 else slobId.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false

        other as BlobDescriptor
        if (blobId == null) {
            if (other.blobId != null) return false
        } else if (blobId != other.blobId) return false

        if (fragment == null) {
            if (other.fragment != null) return false
        } else if (fragment != other.fragment) return false

        return if (slobId == null) {
            other.slobId == null
        } else slobId == other.slobId
    }

    var slobId: String? = null
    var slobUri: String? = null
    var blobId: String? = null
    var key: String? = null
    var fragment: String? = null

    companion object {
        fun fromUri(uri: Uri): BlobDescriptor? {
            val bd = BlobDescriptor()
            bd.id = UUID.randomUUID().toString()
            bd.createdAt = System.currentTimeMillis()
            bd.lastAccess = bd.createdAt
            val pathSegments = uri.pathSegments
            val segmentCount = pathSegments.size
            if (segmentCount < 3) {
                return null
            }
            bd.slobId = pathSegments[1]
            val key = StringBuilder()
            for (i in 2 until segmentCount) {
                if (key.isNotEmpty()) {
                    key.append("/")
                }
                key.append(pathSegments[i])
            }
            bd.key = key.toString()
            bd.blobId = uri.getQueryParameter("blob")
            bd.fragment = uri.fragment
            return bd
        }
    }
}