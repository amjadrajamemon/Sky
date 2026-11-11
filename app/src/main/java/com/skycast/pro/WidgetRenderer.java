package com.skycast.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Locale;

/** Launcher-safe, responsive RemoteViews. All display components use per-widget settings. */
final class WidgetRenderer {
    private WidgetRenderer() {}

    static RemoteViews render(Context context, Forecast forecast, String city, WidgetSettings settings,
                              boolean fahrenheit, boolean online, int preset, int width, int height) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_panel);
        int w = Math.max(150, width);
        int h = Math.max(90, height);
        float density = context.getResources().getDisplayMetrics().density;
        Bitmap background = WidgetBackdropPainter.make(Math.min(320, Math.round(w * density * .55f)),
                Math.min(180, Math.round(h * density * .55f)),
                settings.background(), settings.accent(), settings.foreground());
        views.setImageViewBitmap(R.id.widget_background, background);
        float scale = settings.textSize() / 100f;
        int layout = settings.layout();
        boolean showCurrent = layout == WidgetSettings.DEFAULT || layout == WidgetSettings.CURRENT
                || layout == WidgetSettings.BOTH || layout == WidgetSettings.TEMP_ONLY
                || layout == WidgetSettings.TEMP_RAIN || layout == WidgetSettings.CURRENT_WEEK
                || layout == WidgetSettings.CURRENT_TODAY;
        boolean showToday = layout == WidgetSettings.DEFAULT || layout == WidgetSettings.BOTH
                || layout == WidgetSettings.TEMP_RAIN || layout == WidgetSettings.TODAY_WEEK
                || layout == WidgetSettings.CURRENT_TODAY || layout == WidgetSettings.TODAY_ONLY;
        boolean showWeek = layout == WidgetSettings.DEFAULT || layout == WidgetSettings.FORECAST
                || layout == WidgetSettings.BOTH || layout == WidgetSettings.TODAY_WEEK
                || layout == WidgetSettings.CURRENT_WEEK;
        if (preset == 2 && layout == WidgetSettings.DEFAULT) showWeek = false;
        boolean hasData = forecast != null;
        boolean rain = settings.show("rain");
        boolean chance = settings.showOption("chance", true);
        boolean amount = settings.showOption("amount", true);
        boolean dayTemp = settings.show("highsLows") && height >= 150;
        boolean weekIcons = settings.show("icons") && height >= 175;
        boolean currentIcons = settings.show("icons") && height >= 124;
        int weekCount = !hasData || !showWeek ? 0 : Math.min(settings.days(), forecast.days.size());
        if (layout == WidgetSettings.DEFAULT || layout == WidgetSettings.BOTH) weekCount = Math.min(7, weekCount);
        boolean tiny = h <= 125;
        int pad = settings.density() == 0 ? 5 : settings.density() == 1 ? 8 : 11;
        views.setViewPadding(R.id.widget_content, dp(context, pad + 2), dp(context, tiny ? 3 : pad),
                dp(context, pad + 2), dp(context, tiny ? 3 : pad));
        views.setViewPadding(R.id.widget_rain_panel, dp(context, 6), dp(context, tiny ? 1 : 3), dp(context, 6), dp(context, tiny ? 1 : 3));

        String title = settings.text("title").trim();
        if (title.isEmpty()) {
            title = city;
            int comma = title.indexOf(',');
            if (comma > 0) title = title.substring(0, comma).trim();
        }
        views.setTextViewText(R.id.widget_city, styled(title, settings));
        visible(views, R.id.widget_city, settings.show("location"));
        text(views, R.id.widget_city, settings.foreground(), settings.size("title", 10), scale);
        text(views, R.id.widget_settings, settings.accent(), 15, 1);
        visible(views, R.id.widget_header, settings.show("location") || settings.showOption("settingsIcon", true));
        visible(views, R.id.widget_settings, settings.showOption("settingsIcon", true));

        visible(views, R.id.widget_message, !hasData);
        if (!hasData) {
            String msg = "Waiting for weather";
            if (!online) msg = "Offline · forecast unavailable";
            if ("Choose your city".equals(city)) msg = "Choose your city in the app";
            views.setTextViewText(R.id.widget_message, styled(msg, settings));
        }
        visible(views, R.id.widget_now, hasData && showCurrent);
        visible(views, R.id.widget_live_label, settings.showOption("liveLabel", true)
                && layout != WidgetSettings.TEMP_ONLY);
        visible(views, R.id.widget_temp, settings.show("temperature"));
        visible(views, R.id.widget_condition, settings.show("condition") && layout != WidgetSettings.TEMP_ONLY);
        visible(views, R.id.widget_icon, currentIcons && layout != WidgetSettings.TEMP_ONLY);
        text(views, R.id.widget_live_label, settings.accent(), settings.size("liveLabel", 9), scale);
        text(views, R.id.widget_temp, settings.foreground(), settings.size("temperature", tiny ? 25 : 30), scale);
        text(views, R.id.widget_condition, settings.conditionColor(), settings.size("condition", 10), scale);
        if (hasData && showCurrent) {
            views.setTextViewText(R.id.widget_temp, styled(ForecastMath.temp(forecast.temperatureC, fahrenheit), settings));
            views.setTextViewText(R.id.widget_condition, styled(forecast.conditionText.isEmpty()
                    ? ForecastMath.condition(forecast.weatherCode, forecast.day) : forecast.conditionText, settings));
            if (currentIcons) views.setImageViewBitmap(R.id.widget_icon,
                    icon(context, forecast.weatherCode, forecast.day, 31, settings.iconSize()));
        }

        boolean showRain = hasData && showToday && rain;
        visible(views, R.id.widget_rain_panel, showRain);
        visible(views, R.id.widget_alert_sub, settings.showOption("timing", true));
        text(views, R.id.widget_alert_head, settings.rainColor(), settings.size("todayRain", tiny ? 12 : 15), scale);
        text(views, R.id.widget_alert_sub, settings.amountColor(), settings.size("rainTiming", tiny ? 9 : 10), scale);
        if (showRain) {
            views.setTextViewText(R.id.widget_alert_head, styled(RainBrief.today(forecast, chance, amount), settings));
            views.setTextViewText(R.id.widget_alert_sub, styled(RainBrief.timing(forecast, Instant.now()), settings));
        }

        visible(views, R.id.widget_week_section, hasData && weekCount > 0);
        visible(views, R.id.widget_week_label, settings.showOption("weekLabel", false));
        text(views, R.id.widget_week_label, settings.dayColor(), settings.size("weekLabel", 8), scale);
        views.setTextViewText(R.id.widget_week_label, styled(weekCount + "-DAY FORECAST", settings));
        views.removeAllViews(R.id.widget_days);
        float compressed = weekCount == 7 && w < 280 ? .85f : 1f;
        for (int position = 0; position < weekCount; position++) {
            int i = settings.reverseWeek() ? weekCount - position - 1 : position;
            Forecast.Day day = forecast.days.get(i);
            RemoteViews cell = new RemoteViews(context.getPackageName(), R.layout.weekly_day_cell);
            cell.setTextViewText(R.id.day_label, styled(shortDay(day.date), settings));
            text(cell, R.id.day_label, settings.foreground(), settings.size("dayName", 8), scale * compressed);
            visible(cell, R.id.day_icon, weekIcons);
            if (weekIcons) cell.setImageViewBitmap(R.id.day_icon,
                    icon(context, day.weatherCode, true, 17, settings.iconSize()));
            visible(cell, R.id.day_temp, dayTemp);
            cell.setTextViewText(R.id.day_temp, styled(ForecastMath.temp(day.highC, fahrenheit).replace("°", "")
                    + "/" + ForecastMath.temp(day.lowC, fahrenheit), settings));
            text(cell, R.id.day_temp, settings.foreground(), settings.size("dayTemp", 8), scale * compressed);
            visible(cell, R.id.day_rain, rain && chance);
            cell.setTextViewText(R.id.day_rain, styled(RainBrief.percent(day.rainProbability), settings));
            text(cell, R.id.day_rain, settings.rainColor(), settings.size("dayChance", 9), scale * compressed);
            visible(cell, R.id.day_mm, rain && amount);
            cell.setTextViewText(R.id.day_mm, styled(RainBrief.compactMm(day.rainMm), settings));
            text(cell, R.id.day_mm, settings.amountColor(), settings.size("dayAmount", 9), scale * compressed);
            views.addView(R.id.widget_days, cell);
        }

        int gravity = settings.align() == 1 ? Gravity.CENTER_HORIZONTAL : settings.align() == 2 ? Gravity.END : Gravity.START;
        views.setInt(R.id.widget_city, "setGravity", gravity | Gravity.CENTER_VERTICAL);
        views.setInt(R.id.widget_alert_head, "setGravity", gravity);
        views.setInt(R.id.widget_alert_sub, "setGravity", gravity);
        String footer = footer(forecast, settings, online);
        visible(views, R.id.widget_footer, !footer.isEmpty() && h >= 155);
        views.setTextViewText(R.id.widget_footer, styled(footer, settings));
        text(views, R.id.widget_footer, settings.conditionColor(), settings.size("footer", 8), scale);
        return views;
    }

    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static Bitmap icon(Context context, int code, boolean daytime, int sizeDp, int percent) {
        int pixels = dp(context, sizeDp * percent / 100f);
        Bitmap bitmap = WeatherIconPainter.make(code, daytime, Math.max(12, Math.min(256, pixels)));
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return bitmap;
    }
    private static String footer(Forecast f, WidgetSettings settings, boolean online) {
        if (f == null) return "";
        StringBuilder footer = new StringBuilder();
        if (settings.showOption("source", false)) footer.append(WidgetSizing.source(f.source));
        if (settings.showOption("updated", false)) {
            if (footer.length() > 0) footer.append(" · ");
            footer.append(f.retrievedAt);
        }
        if (f.stale || !online) {
            if (footer.length() > 0) footer.append(" · ");
            footer.append(!online ? "Offline" : "Cached");
        }
        return footer.toString();
    }
    private static CharSequence styled(String value, WidgetSettings settings) {
        String[] families = {"sans-serif", "sans-serif-medium", "sans-serif-condensed", "serif", "monospace"};
        SpannableString span = new SpannableString(value);
        if (span.length() > 0) span.setSpan(new TypefaceSpan(families[settings.font()]), 0, span.length(), 0);
        return span;
    }
    private static String shortDay(String date) {
        try { return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())); }
        catch (java.time.DateTimeException e) { return "Day"; }
    }
    private static void visible(RemoteViews views, int id, boolean isVisible) {
        views.setViewVisibility(id, isVisible ? View.VISIBLE : View.GONE);
    }
    private static void text(RemoteViews views, int id, int color, float size, float scale) {
        views.setTextColor(id, color);
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, Math.max(7, size * scale));
    }
}
