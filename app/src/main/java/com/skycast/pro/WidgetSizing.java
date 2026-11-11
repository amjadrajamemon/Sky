package com.skycast.pro;

/** Size rules shared by the widget renderer and its regression tests. */
public final class WidgetSizing {
    private WidgetSizing() {}

    public static int weeklyDayCount(int widthDp, int availableDays, float fontScale) {
        // Each weighted cell needs room for two temperatures and its horizontal margins.
        int cellWidth = 2 + Math.round(34 * Math.max(1, fontScale));
        int fittingDays = Math.max(1, (widthDp - 16) / cellWidth);
        return Math.min(availableDays, Math.min(7, fittingDays));
    }

    public static String source(String name) {
        if (name.startsWith("Google WeatherNext")) return "Google WeatherNext 2";
        if (name.startsWith("MSN")) return "MSN · Foreca";
        if (name.startsWith("AccuWeather")) return "AccuWeather";
        return name.startsWith("Open-Meteo · ") ? name.substring("Open-Meteo · ".length()) : name;
    }
}
