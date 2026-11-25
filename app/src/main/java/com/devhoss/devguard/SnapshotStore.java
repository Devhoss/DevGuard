package com.devhoss.devguard;

import android.content.Context;
import android.content.SharedPreferences;

/** Local, private storage for the single saved development configuration. */
public final class SnapshotStore {

    static final String PREFS = "devguard_state";
    private static final String KEY_SNAPSHOT = "snapshot";

    private SnapshotStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Snapshot load(Context context) {
        return Snapshot.fromJson(prefs(context).getString(KEY_SNAPSHOT, null));
    }

    public static boolean exists(Context context) {
        return load(context) != null;
    }

    /** Reads the live system state and stores it, replacing any existing snapshot. */
    public static Snapshot capture(Context context) {
        Snapshot snapshot = new Snapshot(
                DevSettings.isDevOptionsEnabled(context),
                DevSettings.isUsbEnabled(context),
                DevSettings.supportsWireless()
                        ? (DevSettings.isWirelessEnabled(context)
                        ? Snapshot.WIRELESS_ON : Snapshot.WIRELESS_OFF)
                        : Snapshot.WIRELESS_UNKNOWN,
                System.currentTimeMillis(),
                Snapshot.VERSION);
        save(context, snapshot);
        return snapshot;
    }

    /** Persists a snapshot. commit(), not apply(): it must be on disk before a reboot. */
    public static boolean save(Context context, Snapshot snapshot) {
        return prefs(context).edit()
                .putString(KEY_SNAPSHOT, snapshot.toJson().toString()).commit();
    }

    /** Captures only when nothing is saved yet — the safe-mode contract. */
    public static Snapshot captureIfAbsent(Context context) {
        Snapshot existing = load(context);
        return existing != null ? existing : capture(context);
    }

    public static void forget(Context context) {
        prefs(context).edit().remove(KEY_SNAPSHOT).commit();
    }
}
