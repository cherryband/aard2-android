package space.cherryband.ari.util

import android.net.Uri
import android.util.Log

object Util {
    val TAG = Util::class.java.simpleName

    fun <T> MutableList<T>.safeSort(comparator: Comparator<in T>?) {
        try {
            comparator?.let { sortWith(it) }
        } catch (e: Exception) {
            //From http://www.oracle.com/technetwork/java/javase/compatibility-417013.html#source
            /*
            Synopsis: Updated sort behavior for Arrays and Collections may throw an IllegalArgumentException
            Description: The sorting algorithm used by java.util.Arrays.sort and (indirectly) by
                         java.util.Collections.sort has been replaced. The new sort implementation may
                         throw an IllegalArgumentException if it detects a Comparable that violates
                         the Comparable contract. The previous implementation silently ignored such a situation.
                         If the previous behavior is desired, you can use the new system property,
                         java.util.Arrays.useLegacyMergeSort, to restore previous mergesort behavior.
            Nature of Incompatibility: behavioral
            RFE: 6804124
             */
            //Name comparators use ICU collation key comparison. Given Unicode collation complexity
            //it's hard to be sure that collation key comparisons won't trigger an exception. It certainly
            //does at least for some keys in ICU 53.1.
            //Incorrect or no sorting seems preferable than a crashing app.
            //Note: Kotlin sortWith(Comparator) function is implemented using java.util.Arrays.sort.
            //TODO perhaps java.util.Collections.sort shouldn't be used at all
            Log.w(TAG, "Error while sorting:", e)
        }
    }

    fun wikipediaToSlobUri(uri: Uri): String? {
        val host = uri.host
        if (host == null || host.trim { it <= ' ' }.isEmpty()) {
            return null
        }
        var normalizedHost: String = host
        val parts = host.split('.').toTypedArray()
        //if mobile host like en.m.wikipedia.opr get rid of m
        if (parts.size == 4) {
            normalizedHost = String.format("%s.%s.%s", parts[0], parts[2], parts[3])
        }
        return "http://$normalizedHost"
    }
}