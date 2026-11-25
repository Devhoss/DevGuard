package com.devhoss.devguard;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe Mode and Development Mode transitions.
 *
 * Safe Mode captures a snapshot only if none exists, then switches debugging off.
 * Development Mode restores exactly what the snapshot recorded — never "everything on".
 * Every write is read back; an outcome is only {@link Status#SUCCESS} when the device
 * actually matches the requested state for every setting.
 */
public final class ModeController {

    public enum Status {
        /** Every requested setting holds the requested value. */
        SUCCESS,
        /** The mode was applied, but at least one setting could not be changed. */
        PARTIAL,
        /** Nothing was applied: no permission, or the mode itself could not change. */
        FAILURE
    }

    public static final class Outcome {
        public final Status status;
        public final String message;

        Outcome(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        public boolean ok() {
            return status == Status.SUCCESS;
        }
    }

    private ModeController() {
    }

    public static Outcome activateSafeMode(Context context) {
        if (!DevSettings.hasPermission(context)) {
            return new Outcome(Status.FAILURE, context.getString(R.string.err_no_permission));
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
        DevSettings.setDevOptionsEnabled(context, false);

        RefreshCenter.refreshAll(context);

        // Report what the device actually reads now, not what the writes returned.
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
            return new Outcome(Status.SUCCESS, context.getString(R.string.result_safe_ok));
        }

        String message = context.getString(R.string.result_safe_partial, join(stillOn));
        if (wirelessOn && wirelessResult == DevSettings.Result.REJECTED) {
            message = message + " " + context.getString(R.string.reason_rejected) + ".";
        }
        // Developer Options still on means the device is not in Safe Mode at all.
        Status status = DevSettings.isDevOptionsEnabled(context) ? Status.FAILURE : Status.PARTIAL;
        return new Outcome(status, message);
    }

    public static Outcome restoreDevelopmentMode(Context context) {
        if (!DevSettings.hasPermission(context)) {
            return new Outcome(Status.FAILURE, context.getString(R.string.err_no_permission));
        }
        Snapshot snapshot = SnapshotStore.load(context);
        if (snapshot == null) {
            // No saved configuration: enable the master switch only, never guess debugging.
            DevSettings.Result result = DevSettings.setDevOptionsEnabled(context, true);
            RefreshCenter.refreshAll(context);
            if (result != DevSettings.Result.OK || !DevSettings.isDevOptionsEnabled(context)) {
                return new Outcome(Status.FAILURE, context.getString(R.string.result_restore_failed,
                        Actions.errorMessage(context,
                                DevSettings.label(context, DevSettings.KEY_DEV_OPTIONS), result)));
            }
            return new Outcome(Status.SUCCESS, context.getString(R.string.result_restore_no_snapshot));
        }

        // 1. Developer Options first: the other two settings depend on it.
        DevSettings.setDevOptionsEnabled(context, snapshot.devOptions);
        if (DevSettings.isDevOptionsEnabled(context) != snapshot.devOptions) {
            RefreshCenter.refreshAll(context);
            return new Outcome(Status.FAILURE, context.getString(R.string.result_restore_failed,
                    context.getString(R.string.reason_dev_first)));
        }

        List<String> issues = new ArrayList<>();

        // 2. USB debugging — enforced both ways, so a saved "off" really ends up off.
        if (DevSettings.isUsbEnabled(context) != snapshot.usb) {
            DevSettings.Result usb = DevSettings.setUsbEnabled(context, snapshot.usb);
            if (DevSettings.isUsbEnabled(context) != snapshot.usb) {
                issues.add(Actions.errorMessage(context,
                        DevSettings.label(context, DevSettings.KEY_USB), usb));
            }
        }

        // 3. Wireless debugging — Android 11+ only, and unknown snapshots are left alone.
        if (DevSettings.supportsWireless()
                && snapshot.wireless != Snapshot.WIRELESS_UNKNOWN) {
            boolean desired = snapshot.wireless == Snapshot.WIRELESS_ON;
            boolean wifiConnected = DevSettings.isWifiConnected(context);
            DevSettings.Result wifi = DevSettings.Result.OK;
            if (DevSettings.isWirelessEnabled(context) != desired) {
                wifi = DevSettings.setWirelessEnabled(context, desired);
            }
            if (DevSettings.isWirelessEnabled(context) != desired) {
                String issue = Actions.errorMessage(context,
                        DevSettings.label(context, DevSettings.KEY_WIRELESS), wifi);
                if (desired && !wifiConnected) {
                    issue = issue + " " + context.getString(R.string.reason_wifi_off) + ".";
                }
                issues.add(issue);
            } else if (desired && !wifiConnected) {
                // Android accepts the flag without a transport and then tears wireless
                // debugging down again, so a wireless link was not actually restored.
                issues.add(context.getString(R.string.result_restore_wifi_failed,
                        context.getString(R.string.reason_wifi_off)));
            }
        }

        RefreshCenter.refreshAll(context);

        if (!issues.isEmpty()) {
            return new Outcome(Status.PARTIAL,
                    context.getString(R.string.result_restore_partial, join(issues)));
        }

        return new Outcome(Status.SUCCESS, context.getString(R.string.result_restore_ok));
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
