package com.devhoss.devguard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

/** Builds widget content from the real system settings and keeps both sizes in sync. */
public final class Widgets {

    public static final String ACTION_MODE = "com.devhoss.devguard.widget.MODE";
    public static final String ACTION_TOGGLE_DEV = "com.devhoss.devguard.widget.TOGGLE_DEV";
    public static final String ACTION_TOGGLE_USB = "com.devhoss.devguard.widget.TOGGLE_USB";
    public static final String ACTION_TOGGLE_WIFI = "com.devhoss.devguard.widget.TOGGLE_WIFI";

    private static final int REQ_MODE = 100;
    private static final int REQ_OPEN = 101;
    private static final int REQ_DEV = 102;
    private static final int REQ_USB = 103;
    private static final int REQ_WIFI = 104;

    private Widgets() {
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return;
        }
        updateProvider(context, manager, PillWidgetProvider.class, R.layout.widget_pill);
        updateProvider(context, manager, CompactWidgetProvider.class, R.layout.widget_compact);
        updateProvider(context, manager, ExpandedWidgetProvider.class, R.layout.widget_expanded);
    }

    private static void updateProvider(Context context, AppWidgetManager manager,
                                       Class<?> provider, int layout) {
        int[] ids;
        try {
            ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        } catch (RuntimeException e) {
            return;
        }
        if (ids == null || ids.length == 0) {
            return;
        }
        for (int id : ids) {
            int[] cell;
            try {
                cell = cellSize(context, manager.getAppWidgetOptions(id));
            } catch (RuntimeException e) {
                cell = new int[]{0, 0};
            }
            manager.updateAppWidget(id, build(context, layout, cell[0], cell[1]));
        }
    }

    /**
     * Current cell size in dp, as handed to this widget by the launcher. Options carry the
     * portrait size as the minimum and the landscape size as the maximum; a few launchers
     * report only the current size. Returns {0, 0} when no size is known.
     */
    private static int[] cellSize(Context context, Bundle options) {
        if (options == null) {
            return new int[]{0, 0};
        }
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int width = options.getInt(landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (width <= 0) {
            width = options.getInt(landscape
                    ? AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                    : AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        }
        int height = options.getInt(landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        if (height <= 0) {
            height = options.getInt(landscape
                    ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        }
        return new int[]{width, height};
    }

    /**
     * A rotated home screen can give the flat pill a full-height column. Past that point the
     * horizontal row has nothing left to squeeze, so the switch turns upright.
     */
    private static boolean isTallCell(int widthDp, int heightDp) {
        return widthDp > 0 && heightDp > 0 && heightDp * 2 >= widthDp * 3;
    }

    /** Colours for the widget text and status pills, depending on the surface material. */
    private static final class Palette {
        final int textColor;
        final int dimColor;
        final int offPillBackground;
        final int offPillText;
        final int offDot;

        Palette(Context context, boolean clear) {
            textColor = context.getColor(clear ? R.color.clear_text : R.color.widget_text);
            dimColor = context.getColor(clear ? R.color.clear_text_dim : R.color.widget_dim);
            offPillBackground = clear ? R.drawable.bg_widget_pill_clear : R.drawable.bg_widget_pill_off;
            offPillText = dimColor;
            offDot = clear ? R.drawable.dot_clear_off : R.drawable.dot_off;
        }
    }

    static RemoteViews build(Context context, int layout) {
        return build(context, layout, 0, 0);
    }

    static RemoteViews build(Context context, int layout, int widthDp, int heightDp) {
        if (layout == R.layout.widget_pill && isTallCell(widthDp, heightDp)) {
            layout = R.layout.widget_pill_tall;
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        final Palette palette = new Palette(context, WidgetSurface.isClear(context));
        final int navy = context.getColor(R.color.navy);

        boolean hasPermission = DevSettings.hasPermission(context);
        boolean dev = DevSettings.isDevOptionsEnabled(context);
        boolean usb = DevSettings.isUsbEnabled(context);
        boolean wifiSupported = DevSettings.supportsWireless();
        boolean wifi = wifiSupported && DevSettings.isWirelessEnabled(context);

        boolean pill = layout == R.layout.widget_pill || layout == R.layout.widget_pill_tall;
        int rootId = pill ? R.id.w_pill_root : R.id.w_root;
        views.setInt(rootId, "setBackgroundResource", WidgetSurface.backgroundFor(context));

        if (layout == R.layout.widget_pill_tall) {
            // Upright switch: thumb at the top means Development Mode, at the bottom Safe Mode.
            views.setTextColor(R.id.w_pill_brand, palette.textColor);
            views.setInt(R.id.w_pill_track, "setBackgroundResource",
                    dev ? R.drawable.bg_widget_track_on : R.drawable.bg_widget_track_off);
            views.setViewVisibility(R.id.w_pill_thumb_top,
                    dev ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.w_pill_thumb_bottom,
                    dev ? View.GONE : View.VISIBLE);
            views.setContentDescription(R.id.w_pill_root, context.getString(
                    R.string.widget_pill_content,
                    context.getString(dev ? R.string.mode_dev_short : R.string.mode_safe_short)));
            views.setOnClickPendingIntent(R.id.w_pill_root,
                    broadcast(context, ACTION_MODE, REQ_MODE));
            return views;
        }

        if (layout == R.layout.widget_pill) {
            // Nothing but the switch: the whole pill is the toggle.
            views.setTextColor(R.id.w_pill_brand, palette.textColor);
            views.setImageViewResource(R.id.w_pill_switch,
                    dev ? R.drawable.widget_pill_switch_on : R.drawable.widget_pill_switch_off);
            views.setContentDescription(R.id.w_pill_root, context.getString(
                    R.string.widget_pill_content,
                    context.getString(dev ? R.string.mode_dev_short : R.string.mode_safe_short)));
            views.setOnClickPendingIntent(R.id.w_pill_root,
                    broadcast(context, ACTION_MODE, REQ_MODE));
            return views;
        }

        views.setTextColor(R.id.w_brand, palette.textColor);
        views.setTextViewText(R.id.w_mode_chip, context.getString(
                dev ? R.string.mode_dev_short : R.string.mode_safe_short));
        views.setInt(R.id.w_mode_chip, "setBackgroundResource",
                dev ? R.drawable.bg_widget_pill_on : palette.offPillBackground);
        views.setTextColor(R.id.w_mode_chip, dev ? navy : palette.offPillText);

        views.setTextViewText(R.id.w_mode_action, context.getString(
                dev ? R.string.action_safe : R.string.action_restore));
        views.setTextColor(R.id.w_mode_action, navy);
        views.setOnClickPendingIntent(R.id.w_mode_action,
                broadcast(context, ACTION_MODE, REQ_MODE));

        if (layout == R.layout.widget_compact) {
            views.setTextColor(R.id.w_status_line, palette.dimColor);
            CharSequence status;
            if (!hasPermission) {
                status = context.getString(R.string.widget_setup_needed);
            } else {
                status = context.getString(dev ? R.string.tile_state_dev : R.string.tile_state_safe);
            }
            views.setTextViewText(R.id.w_status_line, status);
            views.setOnClickPendingIntent(R.id.w_root, openApp(context));
            return views;
        }

        views.setTextColor(R.id.w_dev_label, palette.textColor);
        views.setTextColor(R.id.w_usb_label, palette.textColor);
        views.setTextColor(R.id.w_wifi_label, palette.textColor);

        // Expanded: three settings with individual controls. Without the permission the
        // real values are still shown — never a fake "Off" — but the rows are not tappable.
        bindRow(context, views, palette, R.id.w_dev_row, R.id.w_dev_dot, R.id.w_dev_value,
                dev, context.getString(R.string.widget_dev_options),
                hasPermission ? ACTION_TOGGLE_DEV : null, REQ_DEV);
        bindRow(context, views, palette, R.id.w_usb_row, R.id.w_usb_dot, R.id.w_usb_value,
                usb, context.getString(R.string.widget_usb),
                hasPermission ? ACTION_TOGGLE_USB : null, REQ_USB);
        if (wifiSupported) {
            bindRow(context, views, palette, R.id.w_wifi_row, R.id.w_wifi_dot, R.id.w_wifi_value,
                    wifi, context.getString(R.string.widget_wifi),
                    hasPermission ? ACTION_TOGGLE_WIFI : null, REQ_WIFI);
        } else {
            bindUnavailable(context, views, palette, R.id.w_wifi_row, R.id.w_wifi_dot,
                    R.id.w_wifi_value, context.getString(R.string.widget_wifi));
        }
        views.setOnClickPendingIntent(R.id.w_root, openApp(context));
        return views;
    }

    private static void bindRow(Context context, RemoteViews views, Palette palette,
                                int rowId, int dotId, int valueId, boolean enabled,
                                String label, String action, int requestCode) {
        views.setImageViewResource(dotId, enabled ? R.drawable.dot_on : palette.offDot);
        views.setTextViewText(valueId,
                context.getString(enabled ? R.string.state_on : R.string.state_off));
        views.setInt(valueId, "setBackgroundResource",
                enabled ? R.drawable.bg_widget_pill_on : palette.offPillBackground);
        views.setTextColor(valueId,
                enabled ? context.getColor(R.color.navy) : palette.offPillText);
        views.setContentDescription(rowId, context.getString(R.string.widget_row_content,
                label, context.getString(enabled ? R.string.state_on : R.string.state_off)));
        if (action != null) {
            views.setOnClickPendingIntent(rowId, broadcast(context, action, requestCode));
        }
    }

    private static void bindUnavailable(Context context, RemoteViews views, Palette palette,
                                        int rowId, int dotId, int valueId, String label) {
        views.setImageViewResource(dotId, palette.offDot);
        views.setTextViewText(valueId, context.getString(R.string.widget_wifi_unavailable));
        views.setInt(valueId, "setBackgroundResource", palette.offPillBackground);
        views.setTextColor(valueId, palette.offPillText);
        views.setContentDescription(rowId, context.getString(R.string.widget_row_content, label,
                context.getString(R.string.widget_wifi_unavailable)));
        views.setOnClickPendingIntent(rowId, openApp(context));
    }

    private static PendingIntent broadcast(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, WidgetActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, REQ_OPEN, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
