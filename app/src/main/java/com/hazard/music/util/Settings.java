package com.hazard.music.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {

    private static final String PREFS = "settings";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_TREE = "download_tree";

    private Settings() {
    }

    public static String quality(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_QUALITY, "lossless");
        if (!"lossless".equals(value) && !"exhigh".equals(value) && !"standard".equals(value)) {
            value = "lossless";
        }
        return value;
    }

    public static void setQuality(Context context, String quality) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_QUALITY, quality).apply();
    }

    /** Preferred level first, then lower fallbacks. */
    public static String[] levels(Context context) {
        String preferred = quality(context);
        if ("standard".equals(preferred)) {
            return new String[]{"standard"};
        }
        if ("exhigh".equals(preferred)) {
            return new String[]{"exhigh", "standard"};
        }
        return new String[]{"lossless", "exhigh", "standard"};
    }

    public static String downloadTree(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TREE, "");
    }

    public static void setDownloadTree(Context context, String uri) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TREE, uri == null ? "" : uri).apply();
    }

    public static String label(String level) {
        if ("lossless".equals(level)) return "无损优先";
        if ("exhigh".equals(level)) return "320 kbps";
        if ("standard".equals(level)) return "标准";
        return level;
    }
}
