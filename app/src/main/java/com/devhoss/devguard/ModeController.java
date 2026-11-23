package com.devhoss.devguard;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe Mode and Development Mode transitions.
 *
 * Safe Mode captures a snapshot only if none exists, then switches debugging off.
 * Development Mode restores exactly what the snapshot recorded — never "everything on".
 * Both verify the system afterwards and report partial results honestly.
 */
public final class ModeController {

    public static final class Outcome {
        public final boolean ok;
        public final String message;

        Outcome(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private ModeController() {
    }

    public static Outcome activateSafeMode(Context context) {
        if (!DevSettings.hasPermission(context)) {
            return new Outcome(false, context.getString(R.string.err_no_permission));
        }
        // Contract: capture once, never overwrite an existing snapshot.
        SnapshotStore.captureIfAbsent(context);

        boolean wirelessOn = DevSettings.supportsWireless() && DevSettings.isWirelessEnabled(context);
        boolean usbOn = DevSettings.isUsbEnabled(context);

        DevSettings.Result wirelessResult = DevSettings.Result.OK;
        if (wirelessOn) {
            wirelessResult = DevSettings.setWirelessEnabled(context, false);
        }
        if (usbOn) {
            DevSettings.setUsbEnabled(context, false);
        }
        // Developer Options last: disabling it also tears ADB down on its own.
        DevSettings.Result devResult = DevSettings.setDevOptionsEnabled(context, false);

        RefreshCenter.refreshAll(context);

        if (devResult != DevSettings.Result.OK && DevSettings.isDevOptionsEnabled(context)) {
            return new Outcome(false, Actions.errorMessage(
                    context, DevSettings.label(context, DevSettings.KEY_DEV_OPTIONS), devResult));
        }

        List<String> stillOn = new ArrayList<>();
        if (DevSettings.isDevOptionsEnabled(context)) {
            stillOn.add(DevSettings.label(context, DevSettings.KEY_DEV_OPTIONS));
        }
        if (DevSettings.isUsbEnabled(context)) {
            stillOn.add(DevSettings.label(context, DevSettings.KEY_USB));
        }
        if (DevSettings.supportsWireless() && DevSettings.isWirelessEnabled(context)) {
            stillOn.add(DevSettings.label(context, DevSettings.KEY_WIRELESS));
        }

        if (stillOn.isEmpty()) {
            return new Outcome(true, context.getString(R.string.result_safe_ok));
        }
        String message = context.getString(R.string.result_safe_partial, join(stillOn));
        if (wirelessResult == DevSettings.Result.REJECTED && wirelessOn) {
            message = message + " " + context.getString(R.string.reason_rejected) + ".";
        }
        return new Outcome(false, message);
    }

    public static Outcome restoreDevelopmentMode(Context context) {
        if (!DevSettings.hasPermission(context)) {
            return new Outcome(false, context.getString(R.string.err_no_permission));
        }
        Snapshot snapshot = SnapshotStore.load(context);
        if (snapshot == null) {
            // No saved configuration: enable the master switch only, never guess debugging.
            DevSettings.Result result = DevSettings.setDevOptionsEnabled(context, true);
            RefreshCenter.refreshAll(context);
            if (result != DevSettings.Result.OK || !DevSettings.isDevOptionsEnabled(context)) {
                return new Outcome(false, context.getString(R.string.result_restore_failed,
                        Actions.errorMessage(context,
                                DevSettings.label(context, DevSettings.KEY_DEV_OPTIONS), result)));
            }
            return new Outcome(true, context.getString(R.string.result_restore_no_snapshot));
        }

        // 1. Developer Options first: the other two settings depend on it.
        DevSettings.Result devResult = DevSettings.setDevOptionsEnabled(context, snapshot.devOptions);
        if (devResult != DevSettings.Result.OK
                || DevSettings.isDevOptionsEnabled(context) != snapshot.devOptions) {
            RefreshCenter.refreshAll(context);
            return new Outcome(false, context.getString(R.string.result_restore_failed,
                    context.getString(R.string.reason_dev_first)));
        }

        if (!snapshot.devOptions) {
            // The saved configuration was itself a safe one; keep debugging off.
            DevSettings.setUsbEnabled(context, false);
            if (DevSettings.supportsWireless()) {
                DevSettings.setWirelessEnabled(context, false);
            }
            RefreshCenter.refreshAll(context);
            return new Outcome(true, context.getString(R.string.result_restore_ok));
        }

        List<String> issues = new ArrayList<>();

        // 2. USB debugging — only if it was on before.
        if (snapshot.usb) {
            DevSettings.Result usb = DevSettings.setUsbEnabled(context, true);
            if (usb != DevSettings.Result.OK || !DevSettings.isUsbEnabled(context)) {
                issues.add(context.getString(R.string.err_verify,
                        DevSettings.label(context, DevSettings.KEY_USB)));
            }
        } else {
            DevSettings.setUsbEnabled(context, false);
        }

        // 3. Wireless debugging — only on Android 11+, and only if requested.
        if (snapshot.wireless == Snapshot.WIRELESS_ON && DevSettings.supportsWireless()) {
            if (!DevSettings.isWifiConnected(context)) {
                issues.add(context.getString(R.string.result_restore_wifi_failed,
                        context.getString(R.string.reason_wifi_off)));
            }
            DevSettings.Result wifi = DevSettings.setWirelessEnabled(context, true);
            if (wifi != DevSettings.Result.OK && DevSettings.isWifiConnected(context)) {
                issues.add(context.getString(R.string.result_restore_wifi_failed,
                        context.getString(R.string.reason_rejected)));
            }
        } else if (snapshot.wireless == Snapshot.WIRELESS_OFF && DevSettings.supportsWireless()) {
            DevSettings.setWirelessEnabled(context, false);
        }

        RefreshCenter.refreshAll(context);

        if (issues.isEmpty()) {
            return new Outcome(true, context.getString(R.string.result_restore_ok));
        }
        return new Outcome(true, join(issues));
    }

    private static String join(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
