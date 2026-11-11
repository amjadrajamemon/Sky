package com.skycast.pro;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Forecast-based rain copy. Hourly data cannot justify minute-precise rain warnings. */
public final class RainInsight {
    private RainInsight() {}
    public static String amount(double mm) {
        if (!Double.isFinite(mm) || mm < 0) return "—mm";
        return (mm >= 10 ? String.format(Locale.US, "%.0f", mm)
                : String.format(Locale.US, "%.1f", mm)) + "mm";
    }
    public static String chance(int chance) { return chance >= 0 && chance <= 100 ? chance + "%" : "—%"; }
    public static String daily(Forecast.Day day) {
        return day == null ? "—% / —mm" : chance(day.rainProbability) + " / " + amount(day.rainMm);
    }
    public static String timing(Forecast forecast, Instant now) {
        if (forecast == null) return "";
        LocalDateTime start;
        try { start = ForecastQuality.hourStart(now, forecast.timezone); }
        catch (RuntimeException invalidTimezone) { return tomorrow(forecast); }
        boolean[] rain = new boolean[48];
        boolean[] observed = new boolean[48];
        for (Forecast.Hour hour : forecast.hours) {
            LocalDateTime time = ForecastQuality.localTime(hour.time);
            if (time == null) continue;
            long index = ChronoUnit.HOURS.between(start, time);
            if (index < 0 || index >= 48) continue;
            int i = (int) index;
            int chance = ForecastQuality.probability(hour.rainProbability);
            double mm = ForecastQuality.precipitation(hour.rainMm);
            if (chance < 0 && !Double.isFinite(mm)) continue;
            observed[i] = true;
            rain[i] = chance >= 50 || (Double.isFinite(mm) && mm >= 0.2);
        }
        int first = -1;
        for (int i = 0; i < 48; i++) if (rain[i]) { first = i; break; }
        if (first < 0) return tomorrow(forecast);
        int duration = 0;
        while (first + duration < 48 && observed[first + duration] && rain[first + duration]) duration++;
        // Never imply a precise prediction, continuous rain or a complete outlook from sparse data.
        String when = first == 0 ? "Rain possible now" : "Rain possible in ~" + first + "h";
        return when + (duration > 0 ? " · ~" + duration + "h window" : "");
    }
    private static String tomorrow(Forecast forecast) {
        if (forecast.days.size() < 2) return "";
        Forecast.Day next = forecast.days.get(1);
        if (next.rainProbability >= 35 || (Double.isFinite(next.rainMm) && next.rainMm >= 0.5))
            return "Tomorrow " + daily(next);
        return "";
    }
}
