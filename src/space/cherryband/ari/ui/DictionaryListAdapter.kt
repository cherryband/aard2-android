package space.cherryband.ari.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.DataSetObserver
import android.net.Uri
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import space.cherryband.ari.R
import space.cherryband.ari.data.SlobDescriptor
import space.cherryband.ari.data.SlobDescriptorList
import space.cherryband.ari.util.IconMaker
import space.cherryband.ari.util.Util
import java.util.*

class DictionaryListAdapter internal constructor(
    private val data: SlobDescriptorList,
    private val context: FragmentActivity?
) : BaseAdapter() {
    private val openUrlOnClick: View.OnClickListener
    private var deleteConfirmationDialog: AlertDialog? = null
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val desc = getItem(position) as SlobDescriptor
        val label = desc.label
        val fileName: String? = try {
            val documentFile = DocumentFile.fromSingleUri(parent.context, Uri.parse(desc.path))!!
            documentFile.name
        } catch (ex: Exception) {
            Log.w(TAG, "Couldn't parse get document file name from uri ${desc.path}", ex)
            desc.path
        }
        val blobCount = desc.blobCount
        val available = data.resolve(desc) != null
        var switchView: SwitchMaterial? = null
        var titleView: TextView? = null
        val toggleIcon = if (desc.expandDetail) IconMaker.IC_ANGLE_UP else IconMaker.IC_ANGLE_DOWN
        val favIcon = if (desc.priority > 0) IconMaker.IC_STAR else IconMaker.IC_STAR_O
        val view: View
        if (convertView == null) {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.dictionary_list_item, parent,
                false
            )

            val detailToggle = View.OnClickListener { detailToggle: View ->
                val pos = detailToggle.tag as Int
                val descriptor = data[pos]
                descriptor.expandDetail = !descriptor.expandDetail
                data[pos] = descriptor
            }
            val toggleFavListener = View.OnClickListener { toggleFav: View ->
                val pos = toggleFav.tag as Int
                val currentTime = System.currentTimeMillis()
                data[pos] = data[pos].apply {
                    priority = if (priority == 0L) currentTime else 0
                    lastAccess = currentTime
                    data.beginUpdate()
                }
                data.sort()
                data.endUpdate(true)
            }

            val licenseView: View = view.findViewById(R.id.dictionary_license)
            val sourceView: View = view.findViewById(R.id.dictionary_source)
            val btnForget: View = view.findViewById(R.id.dictionary_btn_forget)
            val viewDetailToggle: View = view.findViewById(R.id.dictionary_detail_toggle)
            val btnToggleFav: View = view.findViewById(R.id.dictionary_btn_toggle_fav)

            switchView = view.findViewById(R.id.dictionary_active)
            titleView = view.findViewById(R.id.dictionary_label)

            licenseView.setOnClickListener(openUrlOnClick)
            sourceView.setOnClickListener(openUrlOnClick)
            viewDetailToggle.setOnClickListener(detailToggle)
            btnToggleFav.setOnClickListener(toggleFavListener)
            titleView.setOnClickListener(toggleFavListener)
            switchView.setOnClickListener { view14: View ->
                val activeSwitch1 = view14 as SwitchMaterial
                val position14 = view14.getTag() as Int
                val desc13 = data[position14]
                desc13.active = activeSwitch1.isChecked
                data[position14] = desc13
            }
            btnForget.setOnClickListener { forget: View ->
                val pos = forget.tag as Int
                forget(pos)
            }
        } else {
            view = convertView
        }
        val r = parent.resources
        if (switchView == null) switchView = view.findViewById(R.id.dictionary_active)
        if (titleView == null) titleView = view.findViewById(R.id.dictionary_label)
        val detailView: View = view.findViewById(R.id.dictionary_details)
        val btnToggleDetail: ImageView = view.findViewById(R.id.dictionary_btn_toggle_detail)
        val viewDetailToggle: View = view.findViewById(R.id.dictionary_detail_toggle)
        val btnForget: ImageView = view.findViewById(R.id.dictionary_btn_forget)
        val btnToggleFav: ImageView = view.findViewById(R.id.dictionary_btn_toggle_fav)

        switchView!!.isChecked = desc.active
        switchView.tag = position
        titleView!!.isEnabled = available
        titleView.text = label
        titleView.tag = position

        setupBlobCountView(desc, blobCount, available, view, r)
        setupCopyrightView(desc, available, view)
        setupLicenseView(desc, available, view)
        setupSourceView(desc, available, view)
        setupPathView(fileName, available, view)
        setupErrorView(desc, view)
        detailView.visibility = if (desc.expandDetail) View.VISIBLE else View.GONE
        btnToggleDetail.setImageDrawable(IconMaker.list(context, toggleIcon))
        viewDetailToggle.tag = position
        btnForget.setImageDrawable(IconMaker.list(context, IconMaker.IC_TRASH))
        btnForget.tag = position
        btnToggleFav.setImageDrawable(IconMaker.list(context, favIcon))
        btnToggleFav.tag = position
        return view
    }

    private fun setupPathView(path: String?, available: Boolean, view: View) {
        val pathRow: View = view.findViewById(R.id.dictionary_path_row)
        val pathIcon: ImageView = view.findViewById(R.id.dictionary_path_icon)
        val pathView: TextView = view.findViewById(R.id.dictionary_path)
        pathIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_FILE_ARCHIVE))
        pathView.text = path
        pathRow.isEnabled = available
    }

    private fun setupErrorView(desc: SlobDescriptor, view: View) {
        val errorRow: View = view.findViewById(R.id.dictionary_error_row)
        val errorIcon: ImageView = view.findViewById(R.id.dictionary_error_icon)
        val errorView: TextView = view.findViewById(R.id.dictionary_error)
        errorIcon.setImageDrawable(IconMaker.errorText(context, IconMaker.IC_ERROR))
        errorView.text = desc.error
        errorRow.visibility = if (desc.error == null) View.GONE else View.VISIBLE
    }

    private fun setupBlobCountView(
        desc: SlobDescriptor,
        blobCount: Long,
        available: Boolean,
        view: View,
        r: Resources
    ) {
        val blobCountView: TextView = view.findViewById(R.id.dictionary_blob_count)
        blobCountView.apply {
            isEnabled = available
            visibility = if (desc.error == null) View.VISIBLE else View.GONE
            text = String.format(
                Locale.getDefault(),
                r.getQuantityString(R.plurals.dict_item_count, blobCount.toInt()), blobCount
            )
        }
    }

    private fun setupCopyrightView(desc: SlobDescriptor, available: Boolean, view: View) {
        val copyrightRow = view.findViewById<View>(R.id.dictionary_copyright_row)
        val copyrightIcon = view.findViewById<ImageView>(R.id.dictionary_copyright_icon)
        val copyrightView = view.findViewById<TextView>(R.id.dictionary_copyright)
        copyrightIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT))
        val copyright = desc.tags["copyright"]
        copyrightView.text = copyright
        copyrightRow.visibility =
            if (Util.isBlank(copyright)) View.GONE else View.VISIBLE
        copyrightRow.isEnabled = available
    }

    private fun setupSourceView(desc: SlobDescriptor, available: Boolean, view: View) {
        val source = desc.tags["source"]
        val visibility = if (Util.isBlank(source)) View.GONE else View.VISIBLE
        val sourceHtml: CharSequence = Html.fromHtml(String.format(hrefTemplate, source, source))

        val sourceRow: View = view.findViewById(R.id.dictionary_license_row)
        val sourceIcon: ImageView = view.findViewById(R.id.dictionary_source_icon)
        val sourceView: TextView = view.findViewById(R.id.dictionary_source)
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK))
        sourceView.text = sourceHtml
        sourceView.tag = source
        //Setting visibility on layout seems to have no effect
        //if one of the children is a link
        sourceIcon.visibility = visibility
        sourceView.visibility = visibility
        sourceRow.visibility = visibility
        sourceRow.isEnabled = available
    }

    private fun setupLicenseView(desc: SlobDescriptor, available: Boolean, view: View) {
        val licenseName = desc.tags["license.name"]
        val licenseUrl = desc.tags["license.url"]
        val license: CharSequence? = when {
            Util.isBlank(licenseUrl) -> licenseName
            Util.isBlank(licenseName) -> {
                Html.fromHtml(String.format(hrefTemplate, licenseUrl, licenseUrl))
            }
            else -> {
                Html.fromHtml(String.format(hrefTemplate, licenseUrl, licenseName))
            }
        }
        val visibility =
            if (licenseName.isNullOrEmpty() && licenseUrl.isNullOrEmpty()) View.GONE else View.VISIBLE

        val licenseRow: View = view.findViewById(R.id.dictionary_license_row)
        val licenseIcon: ImageView = view.findViewById(R.id.dictionary_license_icon)
        val licenseView: TextView = view.findViewById(R.id.dictionary_license)
        licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE))
        licenseView.text = license
        licenseView.tag = licenseUrl
        licenseIcon.visibility = visibility
        licenseView.visibility = visibility
        licenseRow.visibility = visibility
        licenseRow.isEnabled = available
    }

    private fun forget(position: Int) {
        val desc = data[position]
        val label = desc.label
        val message = context?.getString(R.string.dictionaries_confirm_forget, label)
        deleteConfirmationDialog = context?.let {
            AlertDialog.Builder(it)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    data.removeAt(position)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener { deleteConfirmationDialog = null }
                .create()
                .apply { show() }
        }
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItem(position: Int): Any = data[position]

    override fun getCount(): Int = data.size

    companion object {
        private val TAG = DictionaryListAdapter::class.java.name
        private const val hrefTemplate = "<a href='%1\$s'>%2\$s</a>"
    }

    init {
        val observer: DataSetObserver = object : DataSetObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onInvalidated() {
                notifyDataSetInvalidated()
            }
        }
        data.registerDataSetObserver(observer)
        openUrlOnClick = View.OnClickListener { v: View ->
            val url = v.tag as String
            if (url.isNotBlank()) {
                try {
                    val uri = Uri.parse(url)
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                    v.context.startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to launch browser with url $url", e)
                }
            }
        }
    }
}