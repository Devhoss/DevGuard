package com.devhoss.devguard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/** The one-time ADB grant this app needs, and nothing more. */
public final class AdbSetup {

    private AdbSetup() {
    }

    public static String command(Context context) {
        return "adb shell pm grant " + context.getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS";
    }

    public static boolean copyCommand(Context context) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", command(context)));
        return true;
    }
}
