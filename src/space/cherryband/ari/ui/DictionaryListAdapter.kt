package space.cherryband.ari.ui

import android.content.Context
import space.cherryband.ari.data.SlobDescriptorList
import androidx.fragment.app.FragmentActivity
import android.view.ViewGroup
import space.cherryband.ari.data.SlobDescriptor
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import android.view.LayoutInflater
import space.cherryband.ari.R
import space.cherryband.ari.util.IconMaker
import android.content.DialogInterface
import android.database.DataSetObserver
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import space.cherryband.ari.util.Util
import java.lang.Exception
import java.util.*

class DictionaryListAdapter internal constructor(
    private val data: SlobDescriptorList,
    private val context: FragmentActivity
) : RecyclerView.Adapter<DictionaryViewHolder>() {
    private var deleteConfirmationDialog: AlertDialog? = null

    private val detailToggle = { detailToggleView: View ->
        val itemPos = detailToggleView.tag as Int
        val descriptor = data[itemPos]
        descriptor.expandDetail = !descriptor.expandDetail
        data[itemPos] = descriptor
    }

    private val favToggleListener = { favToggleView: View ->
        val itemPos = favToggleView.tag as Int
        val descriptor = data[itemPos]
        val currentTime = System.currentTimeMillis()
        if (descriptor.priority == 0L) {
            descriptor.priority = currentTime
        } else {
            descriptor.priority = 0
        }
        descriptor.lastAccess = currentTime
        data.beginUpdate()
        data[itemPos] = descriptor
        data.sort()
        data.endUpdate(true)
    }

    private val openUrlOnClick = { linkContainer: View ->
        val url = linkContainer.tag as String
        if (!Util.isBlank(url)) {
            try {
                val uri = Uri.parse(url)
                val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                linkContainer.context.startActivity(browserIntent)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to launch browser with url $url", e)
            }
        }
    }

    private val switchOnClick = { activeSwitchView: View ->
        val activeSwitch = activeSwitchView as SwitchMaterial
        val itemPos = activeSwitch.tag as Int
        val descriptor = data[itemPos]
        descriptor.active = activeSwitch.isChecked
        data[itemPos] = descriptor
    }

    private val forgetOnClick = { forgetButton: View ->
        val itemPos = forgetButton.tag as Int
        forget(itemPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionaryViewHolder {
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.dictionary_list_item, parent, false)
        return DictionaryViewHolder(view).apply {
            detailToggleView.setOnClickListener(detailToggle)
            licenseView.setOnClickListener(openUrlOnClick)
            sourceView.setOnClickListener(openUrlOnClick)
            favToggleButton.setOnClickListener(favToggleListener)
            titleView.setOnClickListener(favToggleListener)
            forgetButton.setOnClickListener(forgetOnClick)
            switchView.setOnClickListener(switchOnClick)
        }
    }

    override fun onBindViewHolder(holder: DictionaryViewHolder, position: Int) {
        val desc = data[position]
        val label = desc.label
        var fileName: String?
        try {
            val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(desc.path))!!
            fileName = documentFile.name
        } catch (ex: Exception) {
            fileName = desc.path
            Log.w(TAG, "Couldn't parse get document file name from uri" + desc.path, ex)
        }
        val blobCount = desc.blobCount
        val available = data.resolve(desc) != null
        val r = context.resources
        val favIcon = if (desc.priority > 0) IconMaker.IC_STAR else IconMaker.IC_STAR_O
        val toggleIcon = if (desc.expandDetail) IconMaker.IC_ANGLE_UP else IconMaker.IC_ANGLE_DOWN
        holder.apply {
            switchView.isChecked = desc.active
            switchView.tag = position
            titleView.let{
                it.isEnabled = available
                it.text = label
                it.tag = position
            }
            btnToggleDetail.setImageDrawable(IconMaker.list(context, toggleIcon))
            detailView.visibility = if (desc.expandDetail) View.VISIBLE else View.GONE
            detailToggleView.tag = position
            forgetButton.tag = position
            favToggleButton.setImageDrawable(IconMaker.list(context, favIcon))
            favToggleButton.tag = position
        }
        setupBlobCountView(desc, blobCount, available, holder, r)
        setupCopyrightView(desc, available, holder)
        setupLicenseView(desc, available, holder)
        setupSourceView(desc, available, holder)
        setupPathView(fileName, available, holder)
        setupErrorView(desc, holder)
    }

    private fun setupPathView(path: String?, available: Boolean, holder: DictionaryViewHolder) {
        holder.apply {
            pathView.text = path
            pathRow.isEnabled = available
        }
    }

    private fun setupErrorView(desc: SlobDescriptor, holder: DictionaryViewHolder) {
        holder.apply {
            errorView.text = desc.error
            errorRow.visibility = if (desc.error == null) View.GONE else View.VISIBLE
        }
    }

    private fun setupBlobCountView(
        desc: SlobDescriptor,
        blobCount: Long,
        available: Boolean,
        holder: DictionaryViewHolder,
        r: Resources
    ) {
        holder.blobCountView.apply {
            isEnabled = available
            visibility = if (desc.error == null) View.VISIBLE else View.GONE
            text = String.format(
                Locale.getDefault(),
                r.getQuantityString(R.plurals.dict_item_count, blobCount.toInt()), blobCount
            )
        }
    }

    private fun setupCopyrightView(
        desc: SlobDescriptor,
        available: Boolean,
        holder: DictionaryViewHolder
    ) {
        val copyright = desc.tags["copyright"]
        holder.apply {
            copyrightView.text = copyright
            copyrightRow.visibility = if (Util.isBlank(copyright)) View.GONE else View.VISIBLE
            copyrightRow.isEnabled = available
        }
    }

    private fun setupSourceView(
        desc: SlobDescriptor,
        available: Boolean,
        holder: DictionaryViewHolder
    ) {
        val source = desc.tags["source"]
        val sourceHtml: CharSequence = Html.fromHtml(String.format(hrefTemplate, source, source))
        //Setting visibility on layout seems to have no effect
        //if one of the children is a link
        val isVisible = if (Util.isBlank(source)) View.GONE else View.VISIBLE

        holder.apply {
            sourceIcon.visibility = isVisible
            sourceView.apply {
                text = sourceHtml
                tag = source
                visibility = isVisible
            }
            sourceRow.apply{
                visibility = isVisible
                isEnabled = available
            }
        }
    }

    private fun setupLicenseView(desc: SlobDescriptor, available: Boolean, holder: DictionaryViewHolder) {
        var licenseName = desc.tags["license.name"]
        val licenseUrl = desc.tags["license.url"]
        val license: CharSequence?
        if (Util.isBlank(licenseUrl)) {
            license = licenseName
        } else {
            if (Util.isBlank(licenseName)) {
                licenseName = licenseUrl
            }
            license = Html.fromHtml(String.format(hrefTemplate, licenseUrl, licenseName))
        }
        val isVisible =
            if (Util.isBlank(licenseName) && Util.isBlank(licenseUrl)) View.GONE else View.VISIBLE

        holder.apply {
            licenseView.apply {
                text = license
                tag = licenseUrl
                visibility = isVisible
            }
            licenseIcon.visibility = isVisible
            licenseRow.visibility = isVisible
            licenseRow.isEnabled = available
        }
    }

    private fun forget(position: Int) {
        val desc = data[position]
        val label = desc.label
        val message = context.getString(R.string.dictionaries_confirm_forget, label)
        deleteConfirmationDialog = AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                data.removeAt(position)
                notifyItemRemoved(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        deleteConfirmationDialog!!.setOnDismissListener {
            deleteConfirmationDialog = null
        }
        deleteConfirmationDialog!!.show()
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int = data.size

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
                notifyDataSetChanged()
            }
        }
        data.registerDataSetObserver(observer)
    }
}

class DictionaryViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val titleView: TextView = itemView.findViewById(R.id.dictionary_label)
    val switchView: SwitchMaterial = itemView.findViewById(R.id.dictionary_active)
    val blobCountView: TextView = itemView.findViewById(R.id.dictionary_blob_count)
    val detailView: View = itemView.findViewById(R.id.dictionary_details)
    val detailToggleView: View = itemView.findViewById(R.id.dictionary_detail_toggle)

    val forgetButton: ImageView = itemView.findViewById(R.id.dictionary_btn_forget)
    val favToggleButton: ImageView = itemView.findViewById(R.id.dictionary_btn_toggle_fav)
    val btnToggleDetail: ImageView = itemView.findViewById(R.id.dictionary_btn_toggle_detail)

    val licenseView: TextView = itemView.findViewById(R.id.dictionary_license)
    val licenseRow: View = itemView.findViewById(R.id.dictionary_license_row)
    val licenseIcon: ImageView = itemView.findViewById(R.id.dictionary_license_icon)

    val sourceView: TextView = itemView.findViewById(R.id.dictionary_source)
    val sourceRow: View = itemView.findViewById(R.id.dictionary_license_row)
    val sourceIcon: ImageView = itemView.findViewById(R.id.dictionary_source_icon)

    val copyrightView: TextView = itemView.findViewById(R.id.dictionary_copyright)
    val copyrightRow: View = itemView.findViewById(R.id.dictionary_copyright_row)

    val pathRow: View = itemView.findViewById(R.id.dictionary_path_row)
    val pathView: TextView = itemView.findViewById(R.id.dictionary_path)

    val errorRow: View = itemView.findViewById(R.id.dictionary_error_row)
    val errorView: TextView = itemView.findViewById(R.id.dictionary_error)

    init {
        val context = itemView.context
        forgetButton.setImageDrawable(IconMaker.list(context, IconMaker.IC_TRASH))
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK))
        licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE))

        itemView.findViewById<ImageView>(R.id.dictionary_copyright_icon)
            .setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT))
        itemView.findViewById<ImageView>(R.id.dictionary_error_icon)
            .setImageDrawable(IconMaker.errorText(context, IconMaker.IC_ERROR))
        itemView.findViewById<ImageView>(R.id.dictionary_path_icon)
            .setImageDrawable(IconMaker.text(context, IconMaker.IC_FILE_ARCHIVE))
    }
}
