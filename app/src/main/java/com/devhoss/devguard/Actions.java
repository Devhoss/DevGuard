package com.devhoss.devguard;

import android.content.Context;

/** Applies a single setting change and turns the result into user-facing feedback. */
public final class Actions {

    public static final class Outcome {
        public final boolean ok;
        /** Null when nothing needs to be said. */
        public final String message;

        Outcome(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private Actions() {
    }

    public static Outcome set(Context context, String key, boolean enabled) {
        if (!DevSettings.hasPermission(context)) {
            return new Outcome(false, context.getString(R.string.err_no_permission));
        }
        // Debugging cannot be enabled while the master switch is off.
        if (enabled && !DevSettings.KEY_DEV_OPTIONS.equals(key)
                && !DevSettings.isDevOptionsEnabled(context)) {
            return new Outcome(false, context.getString(R.string.err_need_dev_options));
        }
        final String label = DevSettings.label(context, key);
        DevSettings.Result result;
        if (DevSettings.KEY_DEV_OPTIONS.equals(key)) {
            result = DevSettings.setDevOptionsEnabled(context, enabled);
        } else if (DevSettings.KEY_USB.equals(key)) {
            result = DevSettings.setUsbEnabled(context, enabled);
        } else {
            result = DevSettings.setWirelessEnabled(context, enabled);
        }
        RefreshCenter.refreshAll(context);

        if (result == DevSettings.Result.OK) {
            if (DevSettings.KEY_WIRELESS.equals(key) && enabled
                    && !DevSettings.isWifiConnected(context)) {
                return new Outcome(true, context.getString(R.string.wifi_on_no_network));
            }
            return new Outcome(true, null);
        }
        return new Outcome(false, errorMessage(context, label, result));
    }

    public static Outcome toggle(Context context, String key) {
        boolean enabled;
        if (DevSettings.KEY_DEV_OPTIONS.equals(key)) {
            enabled = DevSettings.isDevOptionsEnabled(context);
        } else if (DevSettings.KEY_USB.equals(key)) {
            enabled = DevSettings.isUsbEnabled(context);
        } else {
            enabled = DevSettings.isWirelessEnabled(context);
        }
        return set(context, key, !enabled);
    }

    static String errorMessage(Context context, String label, DevSettings.Result result) {
        switch (result) {
            case MISSING_PERMISSION:
                return context.getString(R.string.err_no_permission);
            case UNSUPPORTED:
                return context.getString(R.string.err_unsupported, label);
            case REJECTED:
                return context.getString(R.string.err_rejected, label);
            case NOT_CONFIRMED:
            default:
                return context.getString(R.string.err_verify, label);
        }
    }
}
