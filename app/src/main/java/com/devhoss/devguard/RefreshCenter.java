package com.devhoss.devguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.TileService;

/**
 * One call site for "the system state changed". Every surface — open screens,
 * tile and widgets — is refreshed from the real Android setting, never from the
 * value we hoped to write.
 */
public final class RefreshCenter {

    public static final String ACTION_REFRESH = "com.devhoss.devguard.action.REFRESH";

    private RefreshCenter() {
    }

    public static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        Widgets.updateAll(app);
        try {
            TileService.requestListeningState(app,
                    new ComponentName(app, SettingsTileService.class));
        } catch (RuntimeException ignored) {
            // Tile not added to the panel; nothing to refresh.
        }
        app.sendBroadcast(new Intent(ACTION_REFRESH).setPackage(app.getPackageName()));
    }
}
