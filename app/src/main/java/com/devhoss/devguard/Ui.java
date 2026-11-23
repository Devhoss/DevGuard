package com.devhoss.devguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/** Small shared UI helpers. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static AlertDialog.Builder dialog(Context context) {
        return new AlertDialog.Builder(context, R.style.AppTheme_Dialog);
    }

    public static void toast(Context context, CharSequence message) {
        if (message == null || message.length() == 0) {
            return;
        }
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    public static String timestamp(Context context, long millis) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return format.format(new Date(millis));
    }

    public static void finishWithReveal(Activity activity) {
        activity.finish();
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
