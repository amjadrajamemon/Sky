package com.skycast.pro;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import java.util.LinkedHashMap;
import java.util.Map;

public class WeatherWidget extends AppWidgetProvider {
    private static final Class<?>[] PROVIDERS = {WeatherWidget.class, WeeklyWidget.class, CompactWidget.class};

    protected int kind() { return 0; }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            render(context, manager, id, kind());
            RefreshJob.requestWidget(context, id, false);
        }
        RefreshJob.schedule(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                     int id, Bundle options) {
        render(context, manager, id, kind(), options);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            WidgetSettings.remove(context, id);
            RefreshJob.cancelWidget(context, id);
        }
        RefreshJob.schedule(context);
    }

    @Override public void onRestored(Context context, int[] oldIds, int[] newIds) {
        WidgetSettings[] settings = new WidgetSettings[oldIds.length];
        for (int i = 0; i < oldIds.length; i++) settings[i] = WidgetSettings.load(context, oldIds[i]);
        for (int oldId : oldIds) WidgetSettings.remove(context, oldId);
        for (int i = 0; i < Math.min(settings.length, newIds.length); i++) settings[i].save(context, newIds[i]);
    }

    static Map<Integer, Integer> activeWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Map<Integer, Integer> widgets = new LinkedHashMap<>();
        if (manager == null) return widgets;
        for (int i = 0; i < PROVIDERS.length; i++) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, PROVIDERS[i]));
            if (ids != null) for (int id : ids) widgets.put(id, i);
        }
        return widgets;
    }

    static int kindFor(Context context, int id) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        AppWidgetProviderInfo info = manager == null ? null : manager.getAppWidgetInfo(id);
        if (info == null || info.provider == null || !context.getPackageName().equals(info.provider.getPackageName())) return -1;
        for (int i = 0; i < PROVIDERS.length; i++) {
            if (PROVIDERS[i].getName().equals(info.provider.getClassName())) return i;
        }
        return -1;
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        activeWidgets(context).forEach((id, preset) -> render(context, manager, id, preset));
    }

    static void updateOne(Context context, int id) {
        int preset = kindFor(context, id);
        if (preset >= 0) render(context, AppWidgetManager.getInstance(context), id, preset);
    }

    static int[] size(Context context, Bundle options, int preset) {
        boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int width = options == null ? 0 : options.getInt(landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int height = options == null ? 0 : options.getInt(landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (height <= 0 && options != null) height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        return new int[]{width > 0 ? width : preset == 2 ? 150 : 280,
                height > 0 ? height : preset == 0 ? 120 : preset == 1 ? 110 : 115};
    }

    private static void render(Context context, AppWidgetManager manager, int id, int preset) {
        render(context, manager, id, preset, manager.getAppWidgetOptions(id));
    }

    private static void render(Context context, AppWidgetManager manager, int id, int preset, Bundle options) {
        try {
            WidgetSettings settings = WidgetSettings.load(context, id);
            AppPrefs app = new AppPrefs(context);
            AppPrefs scope = settings.forecastPrefs(context, id);
            Forecast forecast = new ForecastStore(scope).load();
            int[] size = size(context, options, preset);
            RemoteViews views = WidgetRenderer.render(context, forecast, scope.city(), settings,
                    settings.fahrenheit(app), WeatherCoordinator.isOnline(context), preset, size[0], size[1]);
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent main = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_card, main);
            views.setOnClickPendingIntent(R.id.widget_settings, WidgetConfigActivity.pendingIntent(context, id));
            manager.updateAppWidget(id, views);
        } catch (RuntimeException error) {
            Log.w("SkyCastWidget", "Widget " + id + " fell back to the setup view", error);
            RemoteViews fallback = new RemoteViews(context.getPackageName(), R.layout.widget_placeholder);
            fallback.setTextViewText(R.id.widget_footer, "Tap to reload widget");
            fallback.setOnClickPendingIntent(R.id.widget_placeholder_root, WidgetConfigActivity.pendingIntent(context, id));
            try { manager.updateAppWidget(id, fallback); }
            catch (RuntimeException rejected) { Log.e("SkyCastWidget", "Launcher rejected the setup view", rejected); }
        }
    }
}
