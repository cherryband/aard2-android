package space.cherryband.ari.util;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;

import com.kazy.fontdrawable.FontDrawable;

import space.cherryband.ari.R;


public class IconMaker {

    public static final String CUSTOM_FONT_PATH = "fontawesome-4.2.0.ttf";

    public static final char IC_SEARCH = '\uf002';
    public static final char IC_BOOKMARK = '\uf02e';
    public static final char IC_BOOKMARK_O = '\uf097';
    public static final char IC_HISTORY = '\uf1da';
    public static final char IC_DICTIONARY = '\uf02d';
    public static final char IC_SETTINGS = '\uf013';
    public static final char IC_RELOAD = '\uf021';
    public static final char IC_FILTER = '\uf0b0';
    public static final char IC_SORT_DESC = '\uf161';
    public static final char IC_SORT_ASC = '\uf160';
    public static final char IC_CLOCK = '\uf017';
    public static final char IC_LIST = '\uf03a';
    public static final char IC_TRASH = '\uf1f8';
    public static final char IC_LICENSE = '\uf19c';
    public static final char IC_EXTERNAL_LINK = '\uf08e';
    public static final char IC_FILE_ARCHIVE = '\uf1c6';
    public static final char IC_ERROR = '\uf071';
    public static final char IC_COPYRIGHT = '\uf1f9';
    public static final char IC_SELECT_ALL = '\uf046';
    public static final char IC_ADD = '\uf067';
    public static final char IC_ANGLE_UP = '\uf106';
    public static final char IC_ANGLE_DOWN = '\uf107';
    public static final char IC_STAR = '\uf005';
    public static final char IC_STAR_O = '\uf006';
    public static final char IC_FOLDER = '\uf07b';
    public static final char IC_LEVEL_UP = '\uf148';
    public static final char IC_BAN = '\uf05e';
    public static final char IC_FULLSCREEN = '\uf065';


    public static FontDrawable make(Context context, char c, int sizeDp, int color) {
        return new FontDrawable.Builder(context, c, CUSTOM_FONT_PATH)
                .setSizeDp(sizeDp)
                .setColor(color)
                .build();
    }

    public static FontDrawable makeWithColorRes(Context context, char c, int sizeDp, int colorRes) {
        return make(context, c, sizeDp, context.getResources().getColor(colorRes));
    }

    public static FontDrawable tab(Context context, char c) {
        return makeWithColorRes(context, c, 21, R.color.tab_icon);
    }

    public static FontDrawable list(Context context, char c) {
        return makeWithColorRes(context, c, 26, R.color.list_icon);
    }

    public static FontDrawable actionBar(Context context, char c) {
        return makeWithColorRes(context, c, 26, R.color.actionbar_icon);
    }

    public static FontDrawable text(Context context, char c) {
        TypedValue typedValue = new TypedValue();
        boolean wasResolved = context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
        if (wasResolved) {
            int color = ContextCompat.getColor(context, typedValue.resourceId);
            return make(context, c, 16, color);
        }
        return makeWithColorRes(context, c, 16, R.color.list_icon);
    }

    public static FontDrawable errorText(Context context, char c) {
        return makeWithColorRes(context, c, 16, android.R.color.holo_red_dark);
    }

    public static FontDrawable emptyView(Context context, char c) {
        return makeWithColorRes(context, c, 52, R.color.empty_view_icon);
    }

}
