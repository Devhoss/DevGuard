package com.devhoss.devguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

/** Dashboard: current mode, the three settings, the saved configuration and setup. */
public class MainActivity extends Activity {

    private Switch swDev;
    private Switch swUsb;
    private Switch swWifi;
    private TextView statusDev;
    private TextView statusUsb;
    private TextView statusWifi;
    private TextView modeTitle;
    private TextView modeDesc;
    private TextView modeAction;
    private TextView snapshotStatus;
    private TextView snapshotDesc;
    private TextView snapshotTime;
    private TextView snapshotRestore;
    private LinearLayout permissionCard;
    private View rowDev;
    private View rowUsb;
    private View rowWifi;

    private boolean binding;
    private boolean receiverRegistered;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            // Coalesce the burst of changes a single write can produce.
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, 120);
        }
    };
    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_main);
        ThemeHelper.applySystemBarIcons(this);

        bindViews();
        bindActions();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeHelper.applySystemBarIcons(this);
        // Keep home-screen widgets truthful whenever the app comes back to the front.
        Widgets.updateAll(this);
        for (String key : DevSettings.ALL_KEYS) {
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(key), false, observer);
        }
        IntentFilter filter = new IntentFilter(RefreshCenter.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, filter);
        }
        receiverRegistered = true;
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
        try {
            getContentResolver().unregisterContentObserver(observer);
        } catch (RuntimeException ignored) {
            // Observer was never registered.
        }
        if (receiverRegistered) {
            try {
                unregisterReceiver(refreshReceiver);
            } catch (RuntimeException ignored) {
                // Already unregistered.
            }
            receiverRegistered = false;
        }
    }

    private void bindViews() {
        swDev = findViewById(R.id.sw_dev);
        swUsb = findViewById(R.id.sw_usb);
        swWifi = findViewById(R.id.sw_wifi);
        statusDev = findViewById(R.id.status_dev);
        statusUsb = findViewById(R.id.status_usb);
        statusWifi = findViewById(R.id.status_wifi);
        modeTitle = findViewById(R.id.mode_title);
        modeDesc = findViewById(R.id.mode_desc);
        modeAction = findViewById(R.id.mode_action);
        snapshotStatus = findViewById(R.id.snapshot_status);
        snapshotDesc = findViewById(R.id.snapshot_desc);
        snapshotTime = findViewById(R.id.snapshot_time);
        snapshotRestore = findViewById(R.id.snapshot_restore);
        permissionCard = findViewById(R.id.permission_card);
        rowDev = findViewById(R.id.row_dev);
        rowUsb = findViewById(R.id.row_usb);
        rowWifi = findViewById(R.id.row_wifi);
    }

    private void bindActions() {
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        CompoundButton.OnCheckedChangeListener listener =
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (binding) {
                            return;
                        }
                        String key;
                        if (buttonView.getId() == R.id.sw_dev) {
                            key = DevSettings.KEY_DEV_OPTIONS;
                        } else if (buttonView.getId() == R.id.sw_usb) {
                            key = DevSettings.KEY_USB;
                        } else {
                            key = DevSettings.KEY_WIRELESS;
                        }
                        applySetting(key, isChecked);
                    }
                };
        swDev.setOnCheckedChangeListener(listener);
        swUsb.setOnCheckedChangeListener(listener);
        swWifi.setOnCheckedChangeListener(listener);

        rowDev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFromRow(DevSettings.KEY_DEV_OPTIONS, swDev);
            }
        });
        rowUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFromRow(DevSettings.KEY_USB, swUsb);
            }
        });
        rowWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!DevSettings.supportsWireless()) {
                    Ui.toast(MainActivity.this, getString(R.string.wifi_requires_android11));
                    return;
                }
                toggleFromRow(DevSettings.KEY_WIRELESS, swWifi);
            }
        });

        modeAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!DevSettings.hasPermission(MainActivity.this)) {
                    openSetup();
                    return;
                }
                if (modeAction.getTag() != null) {
                    return;
                }
                modeAction.setTag(Boolean.TRUE);
                modeAction.setAlpha(0.65f);
                ModeController.Outcome outcome =
                        DevSettings.currentMode(MainActivity.this) == DevSettings.Mode.DEVELOPMENT
                                ? ModeController.activateSafeMode(MainActivity.this)
                                : ModeController.restoreDevelopmentMode(MainActivity.this);
                modeAction.setAlpha(1f);
                modeAction.setTag(null);
                if (outcome.message != null) {
                    Ui.toast(MainActivity.this, outcome.message);
                }
                refresh();
            }
        });

        snapshotRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModeController.Outcome outcome = ModeController.restoreDevelopmentMode(MainActivity.this);
                if (outcome.message != null) {
                    Ui.toast(MainActivity.this, outcome.message);
                }
                refresh();
            }
        });
        findViewById(R.id.snapshot_manage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSnapshotDialog();
            }
        });

        findViewById(R.id.btn_copy_command).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyCommand();
            }
        });
        findViewById(R.id.btn_setup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSetup();
            }
        });
    }

    private void applySetting(String key, boolean desired) {
        Actions.Outcome outcome = Actions.set(this, key, desired);
        if (outcome.message != null) {
            Ui.toast(this, outcome.message);
        }
        refresh();
    }

    private void toggleFromRow(String key, Switch toggle) {
        if (!DevSettings.hasPermission(this)) {
            openSetup();
            return;
        }
        toggle.setChecked(!toggle.isChecked());
    }

    private void refresh() {
        if (isFinishing() || swDev == null) {
            return;
        }
        boolean hasPermission = DevSettings.hasPermission(this);
        boolean dev = DevSettings.isDevOptionsEnabled(this);
        boolean usb = DevSettings.isUsbEnabled(this);
        boolean wifiSupported = DevSettings.supportsWireless();
        boolean wifi = wifiSupported && DevSettings.isWirelessEnabled(this);
        boolean wifiConnected = DevSettings.isWifiConnected(this);
        boolean developmentMode = dev;

        binding = true;
        swDev.setChecked(dev);
        swUsb.setChecked(usb);
        swWifi.setChecked(wifi);
        binding = false;

        swDev.setEnabled(hasPermission);
        swUsb.setEnabled(hasPermission);
        swWifi.setEnabled(hasPermission && wifiSupported);

        rowDev.setAlpha(hasPermission ? 1f : 0.6f);
        rowUsb.setAlpha(hasPermission ? 1f : 0.6f);
        rowWifi.setAlpha(hasPermission && wifiSupported ? 1f : 0.55f);

        statusDev.setText(dev ? R.string.state_on : R.string.state_off);
        statusDev.setTextColor(getColor(dev ? R.color.accent_ink : R.color.text_secondary));

        if (!dev) {
            statusUsb.setText(R.string.status_needs_dev);
            statusUsb.setTextColor(getColor(R.color.text_secondary));
        } else {
            statusUsb.setText(usb ? R.string.state_on : R.string.state_off);
            statusUsb.setTextColor(getColor(usb ? R.color.accent_ink : R.color.text_secondary));
        }

        if (!wifiSupported) {
            statusWifi.setText(R.string.wifi_requires_android11);
            statusWifi.setTextColor(getColor(R.color.warn_ink));
        } else if (!dev) {
            statusWifi.setText(R.string.status_needs_dev);
            statusWifi.setTextColor(getColor(R.color.text_secondary));
        } else if (wifi) {
            statusWifi.setText(getString(R.string.state_on)
                    + (wifiConnected ? "" : getString(R.string.wifi_not_connected_suffix)));
            statusWifi.setTextColor(getColor(
                    wifiConnected ? R.color.accent_ink : R.color.warn_ink));
        } else {
            statusWifi.setText(R.string.state_off);
            statusWifi.setTextColor(getColor(R.color.text_secondary));
        }

        modeTitle.setText(developmentMode ? R.string.mode_dev : R.string.mode_safe);
        if (hasPermission) {
            modeDesc.setText(developmentMode ? R.string.mode_dev_desc : R.string.mode_safe_desc);
            modeAction.setText(developmentMode ? R.string.action_safe : R.string.action_restore);
        } else {
            // The setup card sits below the fold; put the permission problem and the way out
            // of it in the card the user sees first.
            modeDesc.setText(R.string.setup_required_body);
            modeAction.setText(R.string.action_setup);
        }
        ((ImageView) findViewById(R.id.mode_icon)).setImageResource(
                developmentMode ? R.drawable.ic_shield : R.drawable.ic_check);

        permissionCard.setVisibility(hasPermission ? View.GONE : View.VISIBLE);

        Snapshot snapshot = SnapshotStore.load(this);
        if (snapshot != null) {
            snapshotStatus.setText(R.string.snapshot_saved);
            snapshotDesc.setText(R.string.snapshot_saved_desc);
            snapshotTime.setVisibility(View.VISIBLE);
            snapshotTime.setText(getString(R.string.snapshot_saved_at,
                    Ui.timestamp(this, snapshot.timestamp))
                    + " · " + getString(R.string.snapshot_version, snapshot.version));
            snapshotRestore.setVisibility(View.VISIBLE);
        } else {
            snapshotStatus.setText(R.string.snapshot_none);
            snapshotDesc.setText(R.string.snapshot_none_desc);
            snapshotTime.setVisibility(View.GONE);
            snapshotRestore.setVisibility(View.GONE);
        }
    }

    private void copyCommand() {
        if (AdbSetup.copyCommand(this)) {
            Ui.toast(this, getString(R.string.command_copied));
        }
    }

    private void openSetup() {
        startActivity(new Intent(this, SetupActivity.class));
    }

    private void showSettings() {
        View content = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        final AlertDialog dialog = Ui.dialog(this).setView(content).create();

        TextView version = content.findViewById(R.id.settings_version);
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            version.setText(getString(R.string.settings_version, versionName));
        } catch (PackageManager.NameNotFoundException e) {
            version.setVisibility(View.GONE);
        }

        RadioGroup group = content.findViewById(R.id.theme_group);
        int mode = ThemeHelper.getMode(this);
        group.check(mode == ThemeHelper.MODE_LIGHT
                ? R.id.theme_light
                : mode == ThemeHelper.MODE_DARK ? R.id.theme_dark : R.id.theme_system);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                int selected = checkedId == R.id.theme_light
                        ? ThemeHelper.MODE_LIGHT
                        : checkedId == R.id.theme_dark ? ThemeHelper.MODE_DARK
                        : ThemeHelper.MODE_SYSTEM;
                if (selected == ThemeHelper.getMode(MainActivity.this)) {
                    return;
                }
                ThemeHelper.setMode(MainActivity.this, selected);
                dialog.dismiss();
                recreate();
            }
        });

        RadioGroup surfaceGroup = content.findViewById(R.id.widget_surface_group);
        surfaceGroup.check(WidgetSurface.getMaterial(this) == WidgetSurface.MATERIAL_CLEAR
                ? R.id.widget_surface_clear : R.id.widget_surface_default);
        surfaceGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int material = checkedId == R.id.widget_surface_clear
                        ? WidgetSurface.MATERIAL_CLEAR : WidgetSurface.MATERIAL_DEFAULT;
                if (material == WidgetSurface.getMaterial(MainActivity.this)) {
                    return;
                }
                WidgetSurface.setMaterial(MainActivity.this, material);
                Widgets.updateAll(MainActivity.this);
            }
        });

        Switch rim = content.findViewById(R.id.sw_specular);
        rim.setChecked(WidgetSurface.hasSpecularRim(this));
        rim.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                WidgetSurface.setSpecularRim(MainActivity.this, isChecked);
                Widgets.updateAll(MainActivity.this);
            }
        });

        content.findViewById(R.id.action_adb).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                openSetup();
            }
        });
        content.findViewById(R.id.action_tile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                requestTile();
            }
        });
        content.findViewById(R.id.action_widget_pill).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                pinWidget(PillWidgetProvider.class);
            }
        });
        content.findViewById(R.id.action_widget_compact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                pinWidget(CompactWidgetProvider.class);
            }
        });
        content.findViewById(R.id.action_widget_expanded).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                pinWidget(ExpandedWidgetProvider.class);
            }
        });

        dialog.show();
    }

    private void showSnapshotDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_snapshot, null);
        final AlertDialog dialog = Ui.dialog(this).setView(content).create();

        final Snapshot snapshot = SnapshotStore.load(this);
        TextView state = content.findViewById(R.id.dialog_snapshot_state);
        TextView detail = content.findViewById(R.id.dialog_snapshot_detail);
        View forget = content.findViewById(R.id.dialog_forget);

        if (snapshot != null) {
            state.setText(R.string.snapshot_saved);
            detail.setText(getString(R.string.snapshot_saved_at,
                    Ui.timestamp(this, snapshot.timestamp))
                    + " · " + getString(R.string.snapshot_version, snapshot.version));
            forget.setVisibility(View.VISIBLE);
        } else {
            state.setText(R.string.snapshot_none);
            detail.setText(R.string.snapshot_none_desc);
            forget.setVisibility(View.GONE);
        }

        content.findViewById(R.id.dialog_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!DevSettings.hasPermission(MainActivity.this)) {
                    dialog.dismiss();
                    openSetup();
                    return;
                }
                SnapshotStore.capture(MainActivity.this);
                Ui.toast(MainActivity.this, getString(R.string.snapshot_saved_toast));
                dialog.dismiss();
                refresh();
            }
        });

        forget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.snapshot_delete_confirm_title)
                        .setMessage(R.string.snapshot_delete_confirm_body)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.snapshot_forget,
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface d, int which) {
                                        SnapshotStore.forget(MainActivity.this);
                                        Ui.toast(MainActivity.this,
                                                getString(R.string.snapshot_forgotten_toast));
                                        dialog.dismiss();
                                        refresh();
                                    }
                                })
                        .show();
            }
        });

        content.findViewById(R.id.dialog_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void requestTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusBarManager statusBar = getSystemService(StatusBarManager.class);
            if (statusBar != null) {
                try {
                    statusBar.requestAddTileService(
                            new ComponentName(this, SettingsTileService.class),
                            getString(R.string.tile_label),
                            Icon.createWithResource(this, R.drawable.ic_tile),
                            getMainExecutor(),
                            result -> {
                                if (result == null || result
                                        == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                                    showTileInstructions();
                                }
                            });
                    return;
                } catch (RuntimeException ignored) {
                    // Fall through to manual instructions.
                }
            }
        }
        showTileInstructions();
    }

    private void showTileInstructions() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.tile_manual_title)
                .setMessage(R.string.tile_manual_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void pinWidget(Class<?> provider) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager == null || !manager.isRequestPinAppWidgetSupported()) {
            Ui.toast(this, getString(R.string.settings_widget_unavailable));
            return;
        }
        try {
            boolean requested = manager.requestPinAppWidget(
                    new ComponentName(this, provider), null, null);
            if (requested) {
                Ui.toast(this, getString(R.string.widget_pin_prompt));
            } else {
                Ui.toast(this, getString(R.string.settings_widget_unavailable));
            }
        } catch (RuntimeException e) {
            Ui.toast(this, getString(R.string.settings_widget_unavailable));
        }
    }
}
