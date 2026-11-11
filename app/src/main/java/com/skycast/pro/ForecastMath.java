package com.skycast.pro;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java calculations, independent of Android and HTTP libraries. */
public final class ForecastMath {
    private ForecastMath() {}

    public static double median(List<Double> values) {
        List<Double> clean = new ArrayList<>();
        for (Double x : values) if (x != null && Double.isFinite(x)) clean.add(x);
        if (clean.isEmpty()) return Double.NaN;
        Collections.sort(clean);
        int i = clean.size() / 2;
        return clean.size()%2 == 0 ? (clean.get(i-1) + clean.get(i))/2 : clean.get(i);
    }

    /** Agreement is not independently verified forecast accuracy. */
    public static String temperatureAgreement(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) return "Unavailable";
        double spread = Math.abs(a-b);
        return spread < 2 ? "Models broadly agree" : spread < 5 ? "Models differ moderately" : "Models disagree significantly";
    }

    public static String condition(int code, boolean daytime) {
        if (code == 0) return daytime ? "Clear" : "Clear night";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code == 51 || code == 53 || code == 55) return "Drizzle";
        if (code == 56 || code == 57) return "Freezing drizzle";
        if (code == 61 || code == 63 || code == 65) return "Rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code == 71 || code == 73 || code == 75 || code == 77) return "Snow";
        if (code == 80 || code == 81 || code == 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code == 95 || code == 96 || code == 99) return "Thunderstorm";
        return "Weather conditions";
    }

    public static String symbol(int code, boolean daytime) {
        if (code == 0) return daytime ? "☀️" : "🌙";
        if (code == 1 || code == 2) return daytime ? "🌤️" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code >= 95) return "⛈️";
        return "🌤️";
    }

    public static final class RainWindow {
        public final int peakChance;
        public final double totalMm;
        public final int firstHour;
        public final int lastHour;
        public final boolean probabilityAvailable;
        public final boolean completeCoverage;
        public final int coveredHours;
        public final int amountHours;
        public final int nextSixHourChance;

        private RainWindow(int peakChance, double totalMm, int firstHour, int lastHour,
                           int coveredHours, int amountHours, int nextSixHourChance) {
            this.peakChance = peakChance;
            this.totalMm = totalMm;
            this.firstHour = firstHour;
            this.lastHour = lastHour;
            this.probabilityAvailable = peakChance >= 0;
            this.completeCoverage = coveredHours == 48;
            this.coveredHours = coveredHours;
            this.amountHours = amountHours;
            this.nextSixHourChance = nextSixHourChance;
        }

        public String headline() {
            if (coveredHours == 0) return "Hourly rain data unavailable";
            if (firstHour < 0) {
                return completeCoverage ? "Low rain signal over 48 h"
                        : "Rain outlook partial · " + coveredHours + " of 48 h";
            }
            String chance = probabilityAvailable ? peakChance + "% peak hourly rain chance"
                    : "Rain forecast (chance unavailable)";
            String amount = amountHours == 0 ? "amount unavailable"
                    : String.format(java.util.Locale.US, "%.1f mm", totalMm)
                            + (amountHours < 48 ? " (partial)" : "");
            return chance + " · " + amount;
        }

        public String period() {
            if (firstHour >= 0) return "Forecast rain signal in " + firstHour + "–" + (lastHour + 1) + " h";
            return coveredHours == 0 ? "Check again after the next update" : "Model forecast";
        }
    }

    /** Clock-relative window used in the app, widget and notification. */
    public static RainWindow rainWindow(Forecast forecast, Instant now) {
        return rainWindow(forecast.hours, ForecastQuality.hourStart(now, forecast.timezone));
    }

    /** Relative evaluation for time-series and pure Java tests. */
    public static RainWindow rainWindow(List<Forecast.Hour> hours) {
        LocalDateTime start = hours.isEmpty() ? null : ForecastQuality.localTime(hours.get(0).time);
        return rainWindow(hours, start);
    }

    private static RainWindow rainWindow(List<Forecast.Hour> hours, LocalDateTime start) {
        int peakChance = -1, nextSixHourChance = -1, first = -1, last = -1;
        int covered = 0, amountHours = 0;
        double total = 0;
        boolean[] seen = new boolean[48];
        for (int i = 0; i < hours.size(); i++) {
            Forecast.Hour h = hours.get(i);
            long offset = i;
            if (start != null) {
                LocalDateTime time = ForecastQuality.localTime(h.time);
                if (time == null || time.isBefore(start)) continue;
                offset = ChronoUnit.HOURS.between(start, time);
            }
            if (offset >= 48 || seen[(int) offset]) continue;
            int hour = (int) offset;
            int chance = ForecastQuality.probability(h.rainProbability);
            double amount = ForecastQuality.precipitation(h.rainMm);
            if (chance < 0 && !Double.isFinite(amount)) continue;
            seen[hour] = true;
            covered++;
            peakChance = Math.max(peakChance, chance);
            if (hour < 6) nextSixHourChance = Math.max(nextSixHourChance, chance);
            if (Double.isFinite(amount)) {
                amountHours++;
                total += amount;
            }
            if (chance >= 35 || (Double.isFinite(amount) && amount >= 0.2)) {
                first = first < 0 ? hour : Math.min(first, hour);
                last = Math.max(last, hour);
            }
        }
        return new RainWindow(peakChance, amountHours == 0 ? Double.NaN : total, first, last,
                covered, amountHours, nextSixHourChance);
    }

    public static String temp(double c, boolean fahrenheit) {
        if (!Double.isFinite(c)) return "—";
        return Math.round(fahrenheit ? c*9/5 + 32 : c) + "°";
    }
}
