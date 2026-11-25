package com.devhoss.devguard;

import org.json.JSONException;
import org.json.JSONObject;

/** A recorded developer configuration with a timestamp and format version. */
public final class Snapshot {

    /** Bump when the persisted shape changes in an incompatible way. */
    public static final int VERSION = 1;

    public static final int WIRELESS_UNKNOWN = -1;
    public static final int WIRELESS_OFF = 0;
    public static final int WIRELESS_ON = 1;

    public final boolean devOptions;
    public final boolean usb;
    /** Tri-state: on, off, or unknown on Android versions without the setting. */
    public final int wireless;
    public final long timestamp;
    public final int version;

    public Snapshot(boolean devOptions, boolean usb, int wireless, long timestamp, int version) {
        this.devOptions = devOptions;
        this.usb = usb;
        this.wireless = wireless;
        this.timestamp = timestamp;
        this.version = version;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("version", version);
            json.put("timestamp", timestamp);
            json.put("devOptions", devOptions);
            json.put("usb", usb);
            json.put("wireless", wireless);
        } catch (JSONException ignored) {
            // JSONObject only rejects null keys, which cannot happen here.
        }
        return json;
    }

    /** Returns null when the stored payload is unreadable or from a newer format. */
    public static Snapshot fromJson(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(raw);
            int version = json.optInt("version", 0);
            if (version < 1 || version > VERSION) {
                return null;
            }
            int wireless = json.optInt("wireless", WIRELESS_UNKNOWN);
            if (wireless < WIRELESS_UNKNOWN || wireless > WIRELESS_ON) {
                wireless = WIRELESS_UNKNOWN;
            }
            return new Snapshot(
                    json.optBoolean("devOptions", true),
                    json.optBoolean("usb", false),
                    wireless,
                    json.optLong("timestamp", 0L),
                    version);
        } catch (JSONException e) {
            return null;
        }
    }
}
