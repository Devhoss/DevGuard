package com.devhoss.devguard;

import android.content.Context;
import android.content.SharedPreferences;

/** Home-screen widget appearance: default card or clear glass, with an optional specular rim. */
public final class WidgetSurface {

    public static final int MATERIAL_DEFAULT = 0;
    public static final int MATERIAL_CLEAR = 1;

    private static final String KEY_MATERIAL = "widget_surface";
    private static final String KEY_RIM = "widget_specular_rim";

    private WidgetSurface() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(SnapshotStore.PREFS, Context.MODE_PRIVATE);
    }

    public static int getMaterial(Context context) {
        return prefs(context).getInt(KEY_MATERIAL, MATERIAL_DEFAULT);
    }

    public static void setMaterial(Context context, int material) {
        prefs(context).edit().putInt(KEY_MATERIAL, material).apply();
    }

    public static boolean isClear(Context context) {
        return getMaterial(context) == MATERIAL_CLEAR;
    }

    /** On by default, matching the reference implementation. */
    public static boolean hasSpecularRim(Context context) {
        return prefs(context).getBoolean(KEY_RIM, true);
    }

    public static void setSpecularRim(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_RIM, enabled).apply();
    }

    /** Background resource for the widget root, chosen from material + rim. */
    public static int backgroundFor(Context context) {
        boolean rim = hasSpecularRim(context);
        if (isClear(context)) {
            return rim ? R.drawable.bg_widget_clear : R.drawable.bg_widget_clear_flat;
        }
        return rim ? R.drawable.bg_widget_card_rim : R.drawable.bg_widget_card;
    }
}
