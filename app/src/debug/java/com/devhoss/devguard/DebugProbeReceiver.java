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
 */
public class DebugProbeReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.devhoss.devguard.DEBUG_PROBE";
    private static final String TAG = "DevGuardProbe";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final int delaySeconds = intent.getIntExtra("delay_seconds", 60);
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
                Log.i(TAG, "restore starting | dev=" + DevSettings.isDevOptionsEnabled(app)
                        + " usb=" + DevSettings.isUsbEnabled(app)
                        + " wifi=" + DevSettings.isWirelessEnabled(app));
                ModeController.Outcome outcome = ModeController.restoreDevelopmentMode(app);
                Log.i(TAG, "restore done ok=" + outcome.ok + " message=" + outcome.message
                        + " | dev=" + DevSettings.isDevOptionsEnabled(app)
                        + " usb=" + DevSettings.isUsbEnabled(app)
                        + " wifi=" + DevSettings.isWirelessEnabled(app));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
