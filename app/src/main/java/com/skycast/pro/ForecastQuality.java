package com.skycast.pro;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TreeMap;

/** Reject invalid required data and keep optional measurements explicitly missing. */
public final class ForecastQuality {
    private ForecastQuality() {}

    public static boolean temperatureValid(double value) {
        return Double.isFinite(value) && value >= -100 && value <= 70;
    }

    public static int probability(int value) {
        return value >= 0 && value <= 100 ? value : -1;
    }

    public static double precipitation(double value) {
        return Double.isFinite(value) && value >= 0 ? value : Double.NaN;
    }

    public static LocalDateTime localTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    public static LocalDateTime hourStart(Instant now, String timezone) {
        return LocalDateTime.ofInstant(now, ZoneId.of(timezone))
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
    }

    public static void prepare(Forecast forecast, Instant now) throws IOException {
        if (!temperatureValid(forecast.temperatureC)) {
            throw new IOException("Invalid current temperature");
        }
        LocalDateTime firstHour;
        try {
            firstHour = hourStart(now, forecast.timezone);
        } catch (java.time.DateTimeException e) {
            throw new IOException("Invalid forecast timezone", e);
        }
        LocalDate today = firstHour.toLocalDate();
        TreeMap<LocalDate, Forecast.Day> days = new TreeMap<>();
        for (Forecast.Day day : forecast.days) {
            LocalDate date;
            try {
                date = LocalDate.parse(day.date);
            } catch (java.time.DateTimeException e) {
                continue;
            }
            if (date.isBefore(today) || !date.isBefore(today.plusDays(7))
                    || !temperatureValid(day.lowC) || !temperatureValid(day.highC)
                    || day.lowC > day.highC) {
                continue;
            }
            day.rainProbability = probability(day.rainProbability);
            day.rainMm = precipitation(day.rainMm);
            days.putIfAbsent(date, day);
        }
        if (days.isEmpty()) {
            throw new IOException("Forecast has no usable daily temperatures");
        }
        forecast.days.clear();
        forecast.days.addAll(days.values());

        TreeMap<LocalDateTime, Forecast.Hour> hours = new TreeMap<>();
        for (Forecast.Hour hour : forecast.hours) {
            LocalDateTime time = localTime(hour.time);
            if (time == null || time.isBefore(firstHour) || !time.isBefore(firstHour.plusHours(48))) {
                continue;
            }
            hour.rainProbability = probability(hour.rainProbability);
            hour.rainMm = precipitation(hour.rainMm);
            if (hour.rainProbability >= 0 || Double.isFinite(hour.rainMm)) {
                hours.putIfAbsent(time, hour);
            }
        }
        forecast.hours.clear();
        forecast.hours.addAll(hours.values());
    }
}
