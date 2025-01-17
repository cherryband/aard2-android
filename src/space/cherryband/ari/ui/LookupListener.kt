package space.cherryband.ari.ui

/**
 * Created by itkach on 9/24/14.
 */
interface LookupListener {
    fun onLookupStarted(query: String?)
    fun onLookupFinished(query: String?)
    fun onLookupCanceled(query: String?)
}