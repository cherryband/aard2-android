/*
 * This file is heavily inspired by the Android Open Source Project
 * licensed under the Apache License, Version 2.0
 */
package space.cherryband.ari.ui

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

open class SearchableWebView(context: Context?, attrs: AttributeSet? = null)
    : WebView(context!!, attrs) {
    private var mLastFind: String? = null
    fun setLastFind(find: String?) {
        mLastFind = find
    }

    /**
     * Start an ActionMode for finding text in this WebView.  Only works if this
     * WebView is attached to the view system.
     *
     * @param query   If non-null, will be the initial text to search for.
     * Otherwise, the last String searched for in this WebView will
     * be used to start.
     * @param showIme If true, show the IME, assuming the user will begin typing.
     * If false and text is non-null, perform a find all.
     * @return boolean True if the find dialog is shown, false otherwise.
     */
    fun showFind(query: String?, showIme: Boolean): Boolean {
        val callback = FindActionModeCallback(context, this)
        if (parent == null || startActionMode(callback) == null) {
            // Could not start the action mode, so end Find on page
            return false
        }
        if (showIme) {
            callback.showSoftInput()
        } else if (query != null) {
            callback.setText(query)
            callback.findAll()
            return true
        }
        if (query != null || mLastFind != null) {
            callback.setText(query ?: mLastFind)
            callback.findAll()
        }
        return true
    }
}