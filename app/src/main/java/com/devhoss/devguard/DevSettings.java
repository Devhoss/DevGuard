package com.devhoss.devguard;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;

/**
 * The only place in the app that reads or writes Android developer settings.
 * Every write is read back and verified before it is reported as successful.
 */
public final class DevSettings {

    public static final String KEY_DEV_OPTIONS = Settings.Global.DEVELOPMENT_SETTINGS_ENABLED;
    public static final String KEY_USB = Settings.Global.ADB_ENABLED;
    /** Wireless debugging exists from Android 11 (API 30) and has no public constant. */
    public static final String KEY_WIRELESS = "adb_wifi_enabled";

    public static final String[] ALL_KEYS = {KEY_DEV_OPTIONS, KEY_USB, KEY_WIRELESS};

    public enum Mode {DEVELOPMENT, SAFE}

    public enum Result {
        /** The system setting now holds the requested value. */
        OK,
        MISSING_PERMISSION,
        UNSUPPORTED,
        /** Android threw while applying the change. */
        REJECTED,
        /** The write went through but the setting still reads the old value. */
        NOT_CONFIRMED
    }

    private DevSettings() {
    }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean supportsWireless() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean isDevOptionsEnabled(Context context) {
        return read(context, KEY_DEV_OPTIONS) == 1;
    }

    public static boolean isUsbEnabled(Context context) {
        return read(context, KEY_USB) == 1;
    }

    public static boolean isWirelessEnabled(Context context) {
        return supportsWireless() && read(context, KEY_WIRELESS) == 1;
    }

    public static Mode currentMode(Context context) {
        return isDevOptionsEnabled(context) ? Mode.DEVELOPMENT : Mode.SAFE;
    }

    public static String label(Context context, String key) {
        if (KEY_DEV_OPTIONS.equals(key)) return context.getString(R.string.dev_options_title);
        if (KEY_USB.equals(key)) return context.getString(R.string.usb_title);
        return context.getString(R.string.wifi_title);
    }

    /*
     * Writing.
     */

    public static Result setDevOptionsEnabled(Context context, boolean enabled) {
        return write(context, KEY_DEV_OPTIONS, enabled);
    }

    public static Result setUsbEnabled(Context context, boolean enabled) {
        return write(context, KEY_USB, enabled);
    }

    public static Result setWirelessEnabled(Context context, boolean enabled) {
        if (!supportsWireless()) {
            return Result.UNSUPPORTED;
        }
        return write(context, KEY_WIRELESS, enabled);
    }

    private static Result write(Context context, String key, boolean enabled) {
        if (!supportsWireless() && KEY_WIRELESS.equals(key)) {
            return Result.UNSUPPORTED;
        }
        if (!hasPermission(context)) {
            return Result.MISSING_PERMISSION;
        }
        if (Faults.rejectWrite(key)) {
            // Debug builds only; the release classifier is a no-op. Lets the device
            // tests prove the app reports a refused write instead of a false success.
            return Result.REJECTED;
        }
        final ContentResolver resolver = context.getContentResolver();
        try {
            Settings.Global.putInt(resolver, key, enabled ? 1 : 0);
        } catch (SecurityException e) {
            return Result.MISSING_PERMISSION;
        } catch (RuntimeException e) {
            return Result.REJECTED;
        }
        return read(context, key) == (enabled ? 1 : 0) ? Result.OK : Result.NOT_CONFIRMED;
    }

    private static int read(Context context, String key) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Wireless debugging needs a Wi-Fi transport. Android may still accept the
     * setting without one, but wireless ADB will not actually come up.
     *
     * <p>The active network is not enough: with a VPN connected it is the VPN, whose
     * capabilities may not expose the Wi-Fi transport. Every network is inspected
     * instead, so a VPN never looks like "Wi-Fi disconnected". Only
     * {@code ACCESS_NETWORK_STATE} is needed — no network access is performed.</p>
     */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network[] networks;
        try {
            // Deprecated since API 31, but still the only synchronous way to enumerate
            // transports without opening a network callback just to ask a yes/no question.
            networks = manager.getAllNetworks();
        } catch (RuntimeException e) {
            return false;
        }
        if (networks == null) {
            return false;
        }
        for (Network network : networks) {
            NetworkCapabilities capabilities;
            try {
                capabilities = manager.getNetworkCapabilities(network);
            } catch (RuntimeException e) {
                continue;
            }
            if (capabilities == null) {
                continue;
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }
}
