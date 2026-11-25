package com.devhoss.devguard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Test seam used to prove that a refused write is reported as such.
 *
 * <p>Only the debug-only {@link DebugProbeReceiver} can populate it; that receiver is not
 * part of release builds, so in a shipped APK nothing can arm this and every write proceeds
 * normally. R8 also removes the unreachable setter and field from the release build.</p>
 */
public final class Faults {

    private static final Set<String> REJECTED =
            Collections.synchronizedSet(new HashSet<String>());

    private Faults() {
    }

    /** Debug-only entry point: settings whose writes must behave like a rejection. */
    static void setRejectedKeys(String[] keys) {
        REJECTED.clear();
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (key != null && key.length() > 0) {
                REJECTED.add(key.trim());
            }
        }
    }

    /** @return true when the write to this setting must behave like an Android rejection. */
    public static boolean rejectWrite(String key) {
        return REJECTED.contains(key);
    }
}
