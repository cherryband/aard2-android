package space.cherryband.ari.ui

import androidx.preference.PreferenceFragmentCompat
import android.os.Bundle
import space.cherryband.ari.R
import android.content.DialogInterface
import space.cherryband.ari.ui.IconMaker
import android.widget.TextView
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.net.Uri
import android.text.Html
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import space.cherryband.ari.ui.SettingsFragment

class SettingsFragment : PreferenceFragmentCompat() {
    private var clearCacheConfirmationDialog: AlertDialog? = null
    private var aboutDialog: AlertDialog? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            "clearCache" -> {
                createClearCacheDialog()
                true
            }
            "about" -> {
                createAboutDialog()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun createAboutDialog() {
        val context = context
        val builder = AlertDialog.Builder(requireActivity())
        val appName = context!!.getString(R.string.app_name)
        val title = context.getString(R.string.setting_about, appName)
        builder.setTitle(title)
            .setNeutralButton(android.R.string.ok) { _, _ -> }
            .setOnDismissListener { _ -> aboutDialog = null }
        aboutDialog = builder.create()
        aboutDialog!!.setContentView(R.layout.settings_about_item)
        val copyrightIcon = aboutDialog!!.findViewById<ImageView>(R.id.setting_about_copyright_icon)

        //copyrightIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_copyright));
        copyrightIcon!!.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT))
        val licenseIcon = aboutDialog!!.findViewById<ImageView>(R.id.setting_about_license_icon)
        licenseIcon!!.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE))
        val sourceIcon = aboutDialog!!.findViewById<ImageView>(R.id.setting_about_source_icon)
        sourceIcon!!.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK))
        val licenseName = context.getString(R.string.application_license_name)
        val licenseUrl = context.getString(R.string.application_license_url)
        val license = context.getString(R.string.application_license, licenseUrl, licenseName)
        val licenseView = aboutDialog!!.findViewById<TextView>(R.id.application_license)
        licenseView!!.setOnClickListener {
            val uri = Uri.parse(licenseUrl)
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(browserIntent)
        }
        licenseView.text = Html.fromHtml(license.trim { it <= ' ' })
        val manager = context.packageManager
        val versionName: String = try {
            val info = manager.getPackageInfo(context.packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
        val version = context.getString(R.string.application_version, versionName)
        val versionView = aboutDialog!!.findViewById<TextView>(R.id.application_version)
        versionView!!.text = Html.fromHtml(version)
        aboutDialog!!.show()
    }

    private fun createClearCacheDialog() {
        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(R.string.confirm_clear_cached_content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val webView = WebView(requireActivity())
                webView.clearCache(true)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setOnDismissListener {
                clearCacheConfirmationDialog = null
            }
        clearCacheConfirmationDialog = builder.create()
        clearCacheConfirmationDialog!!.show()
    }

    /*
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != SettingsListAdapter.CSS_SELECT_REQUEST) {
            Log.d(TAG, String.format("Unknown request code: %d", requestCode));
            return;
        }
        Uri dataUri = data == null ? null : data.getData();
        Log.d(TAG, String.format("req code %s, result code: %s, data: %s", requestCode, resultCode, dataUri));
        if (resultCode == Activity.RESULT_OK && dataUri != null) {
            try {
                InputStream is = getActivity().getContentResolver().openInputStream(dataUri);
                DocumentFile documentFile = DocumentFile.fromSingleUri(getContext(), dataUri);
                String fileName = documentFile.getName();
                AriApplication app = (AriApplication) getActivity().getApplication();
                String userCss = AriApplication.readTextFile(is, 256 * 1024);
                List<String> pathSegments = dataUri.getPathSegments();
                Log.d(TAG, fileName);
                Log.d(TAG, userCss);
                int lastIndexOfDot = fileName.lastIndexOf(".");
                if (lastIndexOfDot > -1) {
                    fileName = fileName.substring(0, lastIndexOfDot);
                }
                if (fileName.length() == 0) {
                    fileName = "???";
                }
                final SharedPreferences prefs = getActivity().getSharedPreferences(
                        "userStyles", Activity.MODE_PRIVATE);

                userCss = userCss.replace("\r", "").replace("\n", "\\n");

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(fileName, userCss);
                boolean saved = editor.commit();
                if (!saved) {
                    Toast.makeText(getActivity(), R.string.msg_failed_to_store_user_style,
                            Toast.LENGTH_LONG).show();
                }
            } catch (AriApplication.FileTooBigException e) {
                Log.d(TAG, "File is too big: " + dataUri);
                Toast.makeText(getActivity(), R.string.msg_file_too_big,
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.d(TAG, "Failed to load: " + dataUri, e);
                Toast.makeText(getActivity(), R.string.msg_failed_to_read_file,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    

    final static int CSS_SELECT_REQUEST = 13;

    private final static String TAG = SettingsListAdapter.class.getSimpleName();

    private List<String> userStyleNames;
    private Map<String, ?> userStyleData;

    private View getUserStylesView(View convertView, final ViewGroup parent) {
        View view;
        LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView != null) {
            view = convertView;
        } else {
            this.userStyleData = userStylePrefs.getAll();
            this.userStyleNames = new ArrayList<>(this.userStyleData.keySet());
            Util.sort(this.userStyleNames);

            view = inflater.inflate(R.layout.settings_user_styles_item, parent,
                    false);
            ImageView btnAdd = view.findViewById(R.id.setting_btn_add_user_style);
            btnAdd.setImageDrawable(IconMaker.list(context, IconMaker.IC_ADD));
            btnAdd.setOnClickListener(view1 -> {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("text/ *");
                Intent chooser = Intent.createChooser(intent, "Select CSS file");
                try {
                    fragment.startActivityForResult(chooser, CSS_SELECT_REQUEST);
                } catch (ActivityNotFoundException e) {
                    Log.d(TAG, "Not activity to get content", e);
                    Toast.makeText(context, R.string.msg_no_activity_to_get_content,
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        View emptyView = view.findViewById(R.id.setting_user_styles_empty);
        emptyView.setVisibility(userStyleNames.size() == 0 ? View.VISIBLE : View.GONE);

        //LinearLayout userStyleListLayout = view.findViewById(R.id.setting_user_styles_list);
        //userStyleListLayout.removeAllViews();
        for (int i = 0; i < userStyleNames.size(); i++) {
            View styleItemView = inflater.inflate(R.layout.user_styles_list_item, parent,
                    false);
            ImageView btnDelete = styleItemView.findViewById(R.id.user_styles_list_btn_delete);
            btnDelete.setImageDrawable(IconMaker.list(context, IconMaker.IC_TRASH));
            btnDelete.setOnClickListener(onDeleteUserStyle);

            String name = userStyleNames.get(i);

            btnDelete.setTag(name);

            TextView nameView = styleItemView.findViewById(R.id.user_styles_list_name);
            nameView.setText(name);

            //userStyleListLayout.addView(styleItemView);
        }

        return view;
    }

    private void deleteUserStyle(final String name) {
        String message = context.getString(R.string.setting_user_style_confirm_forget, name);
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    Log.d(TAG, "Deleting user style " + name);
                    SharedPreferences.Editor edit = userStylePrefs.edit();
                    edit.remove(name);
                    edit.apply();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        this.userStyleData = sharedPreferences.getAll();
        this.userStyleNames = new ArrayList<>(this.userStyleData.keySet());
        Util.sort(userStyleNames);
        notifyDataSetChanged();
    }
*/

    override fun onPause() {
        super.onPause()
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog!!.dismiss()
        }
    }

    companion object {
        private val TAG = SettingsFragment::class.java.simpleName
    }
}