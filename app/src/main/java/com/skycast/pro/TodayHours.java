package com.skycast.pro;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Selects actual remaining hourly periods of *today* in the forecast city's time zone. */
public final class TodayHours {
    private TodayHours() {}

    public static List<Forecast.Hour> remaining(Forecast forecast, Instant now, int limit) {
        List<Forecast.Hour> selected = new ArrayList<>();
        if (forecast == null || now == null || limit < 1) return selected;
        LocalDateTime lower;
        LocalDate day;
        try {
            ZoneId zone = ZoneId.of(forecast.timezone);
            lower = LocalDateTime.ofInstant(now, zone).withMinute(0).withSecond(0).withNano(0);
            day = lower.toLocalDate();
        } catch (DateTimeException e) { return selected; }
        List<Forecast.Hour> sorted = new ArrayList<>(forecast.hours);
        sorted.sort(Comparator.comparing(x -> x.time == null ? "" : x.time));
        LocalDateTime previous = null;
        for (Forecast.Hour hour : sorted) {
            LocalDateTime time = ForecastQuality.localTime(hour.time);
            if (time == null || !day.equals(time.toLocalDate()) || time.isBefore(lower)) continue;
            if (previous != null && previous.equals(time)) continue;
            selected.add(hour);
            previous = time;
            if (selected.size() >= limit) break;
        }
        return selected;
    }

    /** Short 12-hour clock. E.g. 00:00→12a, 15:00→3p. */
    public static String shortTime(String localIso) {
        LocalDateTime time = ForecastQuality.localTime(localIso);
        if (time == null) return "--";
        int hour = time.getHour();
        return (hour % 12 == 0 ? 12 : hour % 12) + (hour >= 12 ? "p" : "a");
    }
}
