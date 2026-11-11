package com.skycast.pro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Honest, terse rain summaries: daily totals and probabilities are never fabricated. */
public final class RainInsights {
    private RainInsights() {}

    public static String chance(Forecast.Day day) {
        return day != null && day.rainProbability >= 0 && day.rainProbability <= 100
                ? day.rainProbability + "%" : "—%";
    }
    public static String amount(Forecast.Day day) {
        if (day == null || !Double.isFinite(day.rainMm) || day.rainMm < 0) return "—mm";
        return (day.rainMm < 10 && day.rainMm > 0 ?
                String.format(Locale.US, "%.1f", day.rainMm) :
                String.format(Locale.US, "%.0f", day.rainMm)) + "mm";
    }
    public static LocalDate localDate(Forecast forecast, Instant now) {
        try { return now.atZone(ZoneId.of(forecast.timezone)).toLocalDate(); }
        catch (RuntimeException error) { return now.atZone(ZoneId.systemDefault()).toLocalDate(); }
    }
    public static Forecast.Day day(Forecast forecast, Instant now, int daysAhead) {
        if (forecast == null) return null;
        LocalDate date = localDate(forecast, now).plusDays(daysAhead);
        for (Forecast.Day day : forecast.days) if (date.toString().equals(day.date)) return day;
        return null; // Cached forecasts must not silently substitute a stale day.
    }
    public static String concise(Forecast.Day day, String prefix) {
        return prefix + " " + chance(day) + " / " + amount(day);
    }

    /** Consecutive *hourly* likely-rain periods, not minute-level nowcasting. */
    public static String timing(Forecast forecast, Instant now) {
        if (forecast == null || forecast.hours.isEmpty()) return "";
        LocalDateTime start = ForecastQuality.hourStart(now, forecast.timezone);
        if (start == null) return "";
        boolean[] rain = new boolean[25];
        boolean[] valid = new boolean[25];
        for (Forecast.Hour hour : forecast.hours) {
            LocalDateTime time = ForecastQuality.localTime(hour.time);
            if (time == null) continue;
            long offset = ChronoUnit.HOURS.between(start, time);
            if (offset < 0 || offset >= rain.length) continue;
            int index = (int) offset;
            int chance = ForecastQuality.probability(hour.rainProbability);
            double mm = ForecastQuality.precipitation(hour.rainMm);
            if (chance < 0 && !Double.isFinite(mm)) continue;
            valid[index] = true;
            rain[index] = chance >= 50 || (Double.isFinite(mm) && mm >= 0.2);
        }
        int first = -1;
        for (int i = 0; i < rain.length; i++) if (valid[i] && rain[i]) { first = i; break; }
        if (first < 0) return "";
        // Avoid claiming continuity across unknown hours.
        int length = 0;
        for (int i = first; i < rain.length && valid[i] && rain[i]; i++) length++;
        String when = first == 0 ? "Rain this hour" : "Rain in ~" + first + "h";
        return when + " · ~" + length + "h forecast";
    }

    public static String alert(Forecast forecast, Instant now) {
        String near = timing(forecast, now);
        if (!near.isEmpty()) return near;
        Forecast.Day tomorrow = day(forecast, now, 1);
        if (tomorrow != null) return concise(tomorrow, "Tomorrow");
        return "";
    }
}
