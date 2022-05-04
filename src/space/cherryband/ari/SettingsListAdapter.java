package space.cherryband.ari;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import space.cherryband.ari.ui.ArticleWebView;
import space.cherryband.ari.ui.IconMaker;
import space.cherryband.ari.util.Util;

public class SettingsListAdapter extends BaseAdapter implements SharedPreferences.OnSharedPreferenceChangeListener {

    final static int CSS_SELECT_REQUEST = 13;

    private final static String TAG = SettingsListAdapter.class.getSimpleName();
    private final FragmentActivity context;
    private final AriApplication app;

    private List<String> userStyleNames;
    private Map<String, ?> userStyleData;
    private final SharedPreferences userStylePrefs;
    private final View.OnClickListener onDeleteUserStyle;
    private final Fragment fragment;


    final static int POS_UI_THEME = 0;
    final static int POS_REMOTE_CONTENT = 1;
    final static int POS_FAV_RANDOM = 2;
    final static int POS_USE_VOLUME_FOR_NAV = 3;
    final static int POS_AUTO_PASTE = 4;
    final static int POS_USER_STYLES = 5;
    final static int POS_CLEAR_CACHE = 6;
    final static int POS_ABOUT = 7;

    SettingsListAdapter(Fragment fragment) {
        this.fragment = fragment;
        this.context = fragment.getActivity();
        this.app = (AriApplication) this.context.getApplication();
        this.userStylePrefs = context.getSharedPreferences(
                "userStyles", AppCompatActivity.MODE_PRIVATE);
        this.userStylePrefs.registerOnSharedPreferenceChangeListener(this);

        this.onDeleteUserStyle = view -> {
            String name = (String) view.getTag();
            deleteUserStyle(name);
        };
    }

    @Override
    public int getCount() {
        return 8;
    }

    @Override
    public Object getItem(int i) {
        return i;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getViewTypeCount() {
        return getCount();
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        switch (i) {
            case POS_UI_THEME:
                return getUIThemeSettingsView(convertView, parent);
            case POS_REMOTE_CONTENT:
                return getRemoteContentSettingsView(convertView, parent);
            case POS_FAV_RANDOM:
                return getFavRandomSwitchView(convertView, parent);
            case POS_USE_VOLUME_FOR_NAV:
                return getUseVolumeForNavView(convertView, parent);
            case POS_AUTO_PASTE:
                return getAutoPasteView(convertView, parent);
            case POS_USER_STYLES:
                return getUserStylesView(convertView, parent);
            case POS_CLEAR_CACHE:
                return getClearCacheView(convertView, parent);
            case POS_ABOUT:
                return getAboutView(convertView, parent);
        }
        return null;
    }

    private View getUIThemeSettingsView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_ui_theme_item, parent,
                    false);

            final SharedPreferences prefs = app.prefs();

            String currentValue = prefs.getString(AriApplication.PREF_UI_THEME,
                    AriApplication.PREF_UI_THEME_SYSTEM);
            Log.d("Settings", AriApplication.PREF_UI_THEME + " current value: " + currentValue);

            View.OnClickListener clickListener = view1 -> {
                SharedPreferences.Editor editor = prefs.edit();
                String value;
                int id = view1.getId();
                if (id == R.id.setting_ui_theme_light) {
                    value = AriApplication.PREF_UI_THEME_LIGHT;
                } else if (id == R.id.setting_ui_theme_dark) {
                    value = AriApplication.PREF_UI_THEME_DARK;
                } else {
                    value = AriApplication.PREF_UI_THEME_SYSTEM;
                }
                Log.d("Settings", AriApplication.PREF_UI_THEME + ": " + value);
                editor.putString(AriApplication.PREF_UI_THEME, value);
                editor.apply();
                context.recreate();
            };
            RadioButton btnLight = view
                    .findViewById(R.id.setting_ui_theme_light);
            RadioButton btnDark = view
                    .findViewById(R.id.setting_ui_theme_dark);
            RadioButton btnSystem = view
                    .findViewById(R.id.setting_ui_theme_system);
            btnLight.setOnClickListener(clickListener);
            btnDark.setOnClickListener(clickListener);
            btnSystem.setOnClickListener(clickListener);
            btnLight.setChecked(currentValue.equals(AriApplication.PREF_UI_THEME_LIGHT));
            btnDark.setChecked(currentValue.equals(AriApplication.PREF_UI_THEME_DARK));
            btnSystem.setChecked(currentValue.equals(AriApplication.PREF_UI_THEME_SYSTEM));
        }
        return view;
    }

    private View getFavRandomSwitchView(View convertView, ViewGroup parent) {
        View view;
        LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AriApplication app = (AriApplication) context.getApplication();
        if (convertView != null) {
            view = convertView;
        } else {
            view = inflater.inflate(R.layout.settings_fav_random_search, parent,
                    false);
            final CheckBox toggle = view.findViewById(R.id.setting_fav_random_search);
            toggle.setOnClickListener(v -> {
                boolean currentValue = app.isOnlyFavDictsForRandomLookup();
                boolean newValue = !currentValue;
                app.setOnlyFavDictsForRandomLookup(newValue);
                toggle.setChecked(newValue);
            });
        }
        boolean currentValue = app.isOnlyFavDictsForRandomLookup();
        CheckBox toggle = view.findViewById(R.id.setting_fav_random_search);
        toggle.setChecked(currentValue);
        return view;
    }

    private View getUseVolumeForNavView(View convertView, ViewGroup parent) {
        View view;
        LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AriApplication app = (AriApplication) context.getApplication();
        if (convertView != null) {
            view = convertView;
        } else {
            view = inflater.inflate(R.layout.settings_use_volume_for_nav, parent,
                    false);
            final CheckBox toggle = view.findViewById(R.id.setting_use_volume_for_nav);
            toggle.setOnClickListener(v -> {
                boolean currentValue = app.getUseVolumeForNav();
                boolean newValue = !currentValue;
                app.setUseVolumeForNav(newValue);
                toggle.setChecked(newValue);
            });
        }
        boolean currentValue = app.getUseVolumeForNav();
        CheckBox toggle = view.findViewById(R.id.setting_use_volume_for_nav);
        toggle.setChecked(currentValue);
        return view;
    }

    private View getAutoPasteView(View convertView, ViewGroup parent) {
        View view;
        LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AriApplication app = (AriApplication) context.getApplication();
        if (convertView != null) {
            view = convertView;
        } else {
            view = inflater.inflate(R.layout.settings_auto_paste, parent,
                    false);
            final CheckBox toggle = view.findViewById(R.id.setting_auto_paste);
            toggle.setOnClickListener(v -> {
                boolean currentValue = app.getAutoPaste();
                boolean newValue = !currentValue;
                app.setAutoPaste(newValue);
                toggle.setChecked(newValue);
            });
        }
        boolean currentValue = app.getAutoPaste();
        CheckBox toggle = view.findViewById(R.id.setting_auto_paste);
        toggle.setChecked(currentValue);
        return view;
    }


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
                intent.setType("text/*");
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

    private View getRemoteContentSettingsView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_remote_content_item, parent,
                    false);

            final SharedPreferences prefs = view.getContext().getSharedPreferences(
                    ArticleWebView.PREF, AppCompatActivity.MODE_PRIVATE);

            String currentValue = prefs.getString(ArticleWebView.PREF_REMOTE_CONTENT,
                    ArticleWebView.PREF_REMOTE_CONTENT_WIFI);
            Log.d("Settings", "Remote content, current value: " + currentValue);

            View.OnClickListener clickListener = view1 -> {
                SharedPreferences.Editor editor = prefs.edit();
                String value = null;
                int id = view1.getId();
                if (id == R.id.setting_remote_content_always) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS;
                } else if (id == R.id.setting_remote_content_wifi) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_WIFI;
                } else if (id == R.id.setting_remote_content_never) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_NEVER;
                }
                Log.d("Settings", "Remote content: " + value);
                if (value != null) {
                    editor.putString(ArticleWebView.PREF_REMOTE_CONTENT, value);
                    editor.apply();
                }
            };
            RadioButton btnAlways = view
                    .findViewById(R.id.setting_remote_content_always);
            RadioButton btnWiFi = view
                    .findViewById(R.id.setting_remote_content_wifi);
            RadioButton btnNever = view
                    .findViewById(R.id.setting_remote_content_never);
            btnAlways.setOnClickListener(clickListener);
            btnWiFi.setOnClickListener(clickListener);
            btnNever.setOnClickListener(clickListener);
            btnAlways.setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS));
            btnWiFi.setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_WIFI));
            btnNever.setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_NEVER));
        }
        return view;
    }

    private View getClearCacheView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            final Context context = parent.getContext();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_clear_cache_item, parent,
                    false);
        }
        return view;
    }

    private View getAboutView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            final Context context = parent.getContext();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_about_item, parent,
                    false);

            ImageView copyrightIcon = view.findViewById(R.id.setting_about_copyright_icon);

            //copyrightIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_copyright));
            copyrightIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT));

            ImageView licenseIcon = view.findViewById(R.id.setting_about_license_icon);
            licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE));

            ImageView sourceIcon = view.findViewById(R.id.setting_about_source_icon);
            sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK));

            String licenseName = context.getString(R.string.application_license_name);
            final String licenseUrl = context.getString(R.string.application_license_url);
            String license = context.getString(R.string.application_license, licenseUrl, licenseName);
            TextView licenseView = view.findViewById(R.id.application_license);
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
            TextView versionView = view.findViewById(R.id.application_version);
            versionView.setText(Html.fromHtml(version));

        }
        return view;
    }

}
