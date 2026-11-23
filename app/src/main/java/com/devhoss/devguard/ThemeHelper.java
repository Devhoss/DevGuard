package com.devhoss.devguard;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;

/** Appearance preference applied by re-resolving resources in a night-mode configuration. */
public final class ThemeHelper {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String KEY_THEME = "theme_mode";

    private ThemeHelper() {
    }

    public static int getMode(Context context) {
        return context.getSharedPreferences(SnapshotStore.PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_THEME, MODE_SYSTEM);
    }

    public static void setMode(Context context, int mode) {
        context.getSharedPreferences(SnapshotStore.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_THEME, mode).apply();
    }

    public static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Wraps a context so activities, dialogs and services resolve the chosen theme. */
    public static Context wrap(Context base) {
        int mode = getMode(base);
        if (mode == MODE_SYSTEM) {
            return base;
        }
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int night = mode == MODE_DARK
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(configuration);
    }

    /** Keeps status and navigation bar icons readable against the window background. */
    public static void applySystemBarIcons(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (isNight(activity)) {
            flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }
}
