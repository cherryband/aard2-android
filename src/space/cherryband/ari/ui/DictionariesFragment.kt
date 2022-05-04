package space.cherryband.ari.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import space.cherryband.ari.AriApplication
import space.cherryband.ari.R

class DictionariesFragment : BaseListFragment() {
    private var listAdapter: DictionaryListAdapter? = null
    override val emptyIcon: Char = IconMaker.IC_DICTIONARY
    override val emptyText: CharSequence
        get() = Html.fromHtml(getString(R.string.main_empty_dictionaries))
    override val supportsSelection = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as AriApplication
        listAdapter = DictionaryListAdapter(app.dictionaries, activity)
        setListAdapter(listAdapter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val result = super.onCreateView(inflater, container, savedInstanceState)
        val extraEmptyView =
            inflater.inflate(R.layout.dictionaries_empty_view_extra, container, false)
        val btn = extraEmptyView.findViewById<Button>(R.id.dictionaries_empty_btn_scan)
        btn.setCompoundDrawablesWithIntrinsicBounds(
            IconMaker.list(activity, IconMaker.IC_ADD),
            null, null, null
        )
        btn.setOnClickListener { selectDictionaryFiles() }
        val emptyViewLayout = emptyView as LinearLayout
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        emptyViewLayout.addView(extraEmptyView, layoutParams)
        return result
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.dictionaries, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val miAddDictionaries = menu.findItem(R.id.action_add_dictionaries)
        miAddDictionaries.icon = IconMaker.actionBar(activity, IconMaker.IC_ADD)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_dictionaries) {
            selectDictionaryFiles()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectDictionaryFiles() {
        val intent = Intent().apply {
            action = Intent.ACTION_OPEN_DOCUMENT
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        try {
            startActivityForResult(intent, FILE_SELECT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Not activity to get content", e)
            Toast.makeText(
                context, R.string.msg_no_activity_to_get_content,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode != FILE_SELECT_REQUEST) {
            Log.d(TAG, "Unknown request code: $requestCode")
            return
        }
        val dataUri = intent?.data
        Log.d(TAG, "req code $requestCode, result code: $resultCode, data: $dataUri")
        
        if (resultCode == Activity.RESULT_OK) {
            val app = requireActivity().application as AriApplication
            val selection: MutableList<Uri> = ArrayList()
            if (dataUri != null) {
                selection.add(dataUri)
            }
            val clipData = intent?.clipData
            if (clipData != null) {
                val itemCount = clipData.itemCount
                (0 until itemCount).mapTo(selection) { clipData.getItemAt(it).uri }
            }
            for (uri in selection) {
                requireActivity().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                app.addDictionary(uri)
            }
        }
    }

    companion object {
        private val TAG = DictionariesFragment::class.java.simpleName
        const val FILE_SELECT_REQUEST = 17
    }
}