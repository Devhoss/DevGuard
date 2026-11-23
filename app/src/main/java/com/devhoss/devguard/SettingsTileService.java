package com.devhoss.devguard;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** One tap from the Quick Settings panel: Safe Mode or restore. */
public class SettingsTileService extends TileService {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!DevSettings.hasPermission(this)) {
            // No permission yet: send the user to setup instead of failing silently.
            openApp();
            return;
        }
        final boolean developmentMode = DevSettings.currentMode(this) == DevSettings.Mode.DEVELOPMENT;
        // Enabling debugging from the lock screen requires the device to be unlocked first.
        unlockAndRun(new Runnable() {
            @Override
            public void run() {
                perform(developmentMode);
            }
        });
    }

    private void perform(boolean developmentMode) {
        ModeController.Outcome outcome = developmentMode
                ? ModeController.activateSafeMode(this)
                : ModeController.restoreDevelopmentMode(this);
        updateTile();
        if (outcome.message != null) {
            Toast.makeText(getApplicationContext(), outcome.message, Toast.LENGTH_LONG).show();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean dev = DevSettings.currentMode(this) == DevSettings.Mode.DEVELOPMENT;
        tile.setState(dev ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.tile_label));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(getString(dev ? R.string.tile_sub_dev : R.string.tile_sub_safe));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.setStateDescription(getString(dev ? R.string.tile_state_dev : R.string.tile_state_safe));
        }
        tile.updateTile();
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (RuntimeException e) {
            startActivity(intent);
        }
    }
}
