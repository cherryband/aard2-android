package space.cherryband.ari.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import space.cherryband.ari.R;

public class SettingsFragment extends PreferenceFragmentCompat {


    private final static String TAG = SettingsFragment.class.getSimpleName();
    private AlertDialog clearCacheConfirmationDialog, aboutDialog;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        if (preference.getKey().equals("clearCache")) {
            createClearCacheDialog();
            return true;
        } if (preference.getKey().equals("about")) {
            createAboutDialog();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void createAboutDialog() {
        final Context context = getContext();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        String appName = context.getString(R.string.app_name);
        String title = context.getString(R.string.setting_about, appName);
        builder.setTitle(title)
                .setNeutralButton(android.R.string.ok, (dialogInterface, i) -> {})
                .setOnDismissListener(dialogInterface -> aboutDialog = null);
        aboutDialog = builder.create();
        aboutDialog.setContentView(R.layout.settings_about_item);

        ImageView copyrightIcon = aboutDialog.findViewById(R.id.setting_about_copyright_icon);

        //copyrightIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_copyright));
        copyrightIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT));

        ImageView licenseIcon = aboutDialog.findViewById(R.id.setting_about_license_icon);
        licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE));

        ImageView sourceIcon = aboutDialog.findViewById(R.id.setting_about_source_icon);
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK));

        String licenseName = context.getString(R.string.application_license_name);
        final String licenseUrl = context.getString(R.string.application_license_url);
        String license = context.getString(R.string.application_license, licenseUrl, licenseName);
        TextView licenseView = aboutDialog.findViewById(R.id.application_license);
        licenseView.setOnClickListener(view1 -> {
            Uri uri = Uri.parse(licenseUrl);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(browserIntent);
        });
        licenseView.setText(Html.fromHtml(license.trim()));

        PackageManager manager = context.getPackageManager();
        String versionName;
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }

        String version = context.getString(R.string.application_version, versionName);
        TextView versionView = aboutDialog.findViewById(R.id.application_version);
        versionView.setText(Html.fromHtml(version));
        aboutDialog.show();
    }

    private void createClearCacheDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setMessage(R.string.confirm_clear_cached_content)
                .setPositiveButton(android.R.string.ok, (dialog, id12) -> {
                    WebView webView = new WebView(requireActivity());
                    webView.clearCache(true);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, id1) -> {
                    // User cancelled the dialog
                })
                .setOnDismissListener(dialogInterface -> clearCacheConfirmationDialog = null);
        clearCacheConfirmationDialog = builder.create();
        clearCacheConfirmationDialog.show();
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
*/

    @Override
    public void onPause() {
        super.onPause();
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog.dismiss();
        }
    }
}
