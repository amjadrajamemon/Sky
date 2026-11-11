package com.skycast.pro;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/** Model agreement is not an observed accuracy or a calibrated confidence percentage. */
public final class RainAgreement {
    private RainAgreement() {}

    private static int signal(Forecast.Hour h) {
        int p = ForecastQuality.probability(h.rainProbability);
        double mm = ForecastQuality.precipitation(h.rainMm);
        if (p >= 55 || (Double.isFinite(mm) && mm >= .25)) return 1;
        if (p >= 0 && p <= 25 && (!Double.isFinite(mm) || mm <= .05)) return 0;
        if (Double.isFinite(mm) && mm <= .05 && p < 0) return 0;
        return -1; // Unknown or ambiguous: do not force agreement.
    }

    /** Returns a qualitative result only where two forecasts cover at least four comparable hours. */
    public static String compare(Forecast primary, Forecast other, LocalDateTime base) {
        if (primary == null || other == null || base == null || primary.stale || other.stale) return "";
        Map<LocalDateTime, Integer> reference = new HashMap<>();
        for (Forecast.Hour h : primary.hours) {
            LocalDateTime hour = ForecastQuality.localTime(h.time);
            if (hour == null) continue;
            long offset = ChronoUnit.HOURS.between(base, hour);
            if (offset >= 0 && offset < 12 && signal(h) >= 0) reference.put(hour, signal(h));
        }
        int compared = 0, agreed = 0, rainy = 0;
        for (Forecast.Hour h : other.hours) {
            LocalDateTime hour = ForecastQuality.localTime(h.time);
            if (hour == null) continue;
            Integer initial = reference.get(hour);
            int value = signal(h);
            if (initial == null || value < 0) continue;
            compared++;
            if (value == initial) agreed++;
            if (value == 1 || initial == 1) rainy++;
        }
        if (compared < 4) return "Limited model evidence";
        if (rainy == 0) return "Two models: low rain signal";
        double consistency = agreed / (double)compared;
        if (consistency >= .8) return "Strong model agreement";
        if (consistency >= .55) return "Moderate model agreement";
        return "Rain models disagree";
    }
}
