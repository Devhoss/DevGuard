package com.devhoss.devguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Debug builds only — never shipped in release.
 *
 * Device testing turns ADB off, which also kills the ADB connection used to drive the
 * test. This receiver schedules a restore through the same production code path
 * ({@link ModeController#restoreDevelopmentMode}) so the connection comes back and the
 * outcome can be inspected in logcat.
 *
 * <p>Extras: {@code delay_seconds} (default 60) and {@code reject}, a comma-separated list
 * of setting keys whose writes should behave like an Android rejection. A local logcat line
 * is written at schedule time, so the state reached while ADB was down can still be read
 * after the connection returns.</p>
 */
public class DebugProbeReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.devhoss.devguard.DEBUG_PROBE";
    private static final String TAG = "DevGuardProbe";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        if (intent.hasExtra("reject")) {
            String raw = intent.getStringExtra("reject");
            Faults.setRejectedKeys(raw == null || raw.length() == 0
                    ? new String[0] : raw.split(","));
            Log.i(TAG, "fault injection: " + (raw == null || raw.length() == 0
                    ? "none" : raw));
        }
        final int delaySeconds = intent.getIntExtra("delay_seconds", 60);
        if (delaySeconds < 0) {
            // Arm-only probe: confirm the injection is live before a restore that may
            // leave debugging off, so a test can never be run half-armed.
            Log.i(TAG, "armed only, no restore | reject=" + intent.getStringExtra("reject"));
            return;
        }
        // Applied when the restore runs, not now: two probes can be queued with different
        // snapshots so the link comes back after a restore that leaves debugging off.
        // Integers, not JSON: a JSON extra is re-quoted by the device shell and lost.
        final boolean keepUsb = intent.getBooleanExtra("keep_usb", false);
        final boolean stage = intent.hasExtra("stage_dev");
        final boolean stagedDev = intent.getIntExtra("stage_dev", 1) == 1;
        final boolean stagedUsb = intent.getIntExtra("stage_usb", 0) == 1;
        final int stagedWifi = intent.getIntExtra("stage_wifi", Snapshot.WIRELESS_UNKNOWN);
        Log.i(TAG, "scheduled restore in " + delaySeconds + "s | dev="
                + DevSettings.isDevOptionsEnabled(app) + " usb=" + DevSettings.isUsbEnabled(app)
                + " wifi=" + DevSettings.isWirelessEnabled(app)
                + " snapshot=" + (SnapshotStore.load(app) != null));

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                if (keepUsb) {
                    // Reboot test only: Safe Mode is supposed to leave USB debugging off, but
                    // then no test link survives. Re-enable just the transport, keep the mode.
                    DevSettings.Result result = DevSettings.setUsbEnabled(app, true);
                    Log.i(TAG, "keep_usb result=" + result + " dev="
                            + DevSettings.isDevOptionsEnabled(app) + " usb="
                            + DevSettings.isUsbEnabled(app) + " wifi="
                            + DevSettings.isWirelessEnabled(app));
                    return;
                }
                if (stage) {
                    boolean saved = SnapshotStore.save(app, new Snapshot(stagedDev, stagedUsb,
                            stagedWifi, System.currentTimeMillis(), Snapshot.VERSION));
                    Log.i(TAG, "stage snapshot: dev=" + stagedDev + " usb=" + stagedUsb
                            + " wifi=" + stagedWifi + " saved=" + saved);
                }
                // This line is written while ADB may already be down; it survives in the
                // logcat ring buffer and proves which state the device actually reached.
                Log.i(TAG, "restore starting | dev=" + DevSettings.isDevOptionsEnabled(app)
                        + " usb=" + DevSettings.isUsbEnabled(app)
                        + " wifi=" + DevSettings.isWirelessEnabled(app)
                        + " wifiConnected=" + DevSettings.isWifiConnected(app));
                ModeController.Outcome outcome = ModeController.restoreDevelopmentMode(app);
                Log.i(TAG, "restore done status=" + outcome.status + " ok=" + outcome.ok()
                        + " message=" + outcome.message
                        + " | dev=" + DevSettings.isDevOptionsEnabled(app)
                        + " usb=" + DevSettings.isUsbEnabled(app)
                        + " wifi=" + DevSettings.isWirelessEnabled(app));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
