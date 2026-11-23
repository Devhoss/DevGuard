package com.devhoss.devguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Executes widget actions, then re-renders the widgets from the real system state. */
public class WidgetActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        Context app = context.getApplicationContext();

        if (Widgets.ACTION_MODE.equals(action)) {
            ModeController.Outcome outcome = DevSettings.currentMode(app) == DevSettings.Mode.DEVELOPMENT
                    ? ModeController.activateSafeMode(app)
                    : ModeController.restoreDevelopmentMode(app);
            if (outcome.message != null) {
                Ui.toast(app, outcome.message);
            }
        } else if (Widgets.ACTION_TOGGLE_DEV.equals(action)) {
            toggle(app, DevSettings.KEY_DEV_OPTIONS);
        } else if (Widgets.ACTION_TOGGLE_USB.equals(action)) {
            toggle(app, DevSettings.KEY_USB);
        } else if (Widgets.ACTION_TOGGLE_WIFI.equals(action)) {
            toggle(app, DevSettings.KEY_WIRELESS);
        } else {
            return;
        }
        Widgets.updateAll(app);
    }

    private static void toggle(Context context, String key) {
        Actions.Outcome outcome = Actions.toggle(context, key);
        if (outcome.message != null) {
            Ui.toast(context, outcome.message);
        }
    }
}
