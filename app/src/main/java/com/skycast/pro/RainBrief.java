package com.skycast.pro;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Display-only, avoids converting precipitation amount into fictional rain probability. */
public final class RainBrief {
    private RainBrief() {}

    public static String compactMm(double mm) {
        if (!Double.isFinite(mm) || mm < 0) return "—mm";
        if (mm >= 100) return Math.round(mm) + "mm";
        return String.format(Locale.US, mm < 10 ? "%.1fmm" : "%.0fmm", mm);
    }
    public static String percent(int chance) { return chance >= 0 && chance <= 100 ? chance + "%" : "—%"; }

    public static String today(Forecast f, boolean showChance, boolean showAmount) {
        return today(f, Instant.now(), showChance, showAmount);
    }
    public static String today(Forecast f, Instant now, boolean showChance, boolean showAmount) {
        Forecast.Day d = f.days.isEmpty() ? null : f.days.get(0);
        try {
            if (d != null && !LocalDate.parse(d.date).equals(now.atZone(ZoneId.of(f.timezone)).toLocalDate()))
                d = null;
        } catch (DateTimeException ex) { d = null; }
        if (d == null) return "TODAY  ·  Rain unavailable";
        StringBuilder text = new StringBuilder("TODAY  ·  ");
        if (showChance) text.append(percent(d.rainProbability));
        if (showChance && showAmount) text.append(" / ");
        if (showAmount) text.append(compactMm(d.rainMm));
        if (!showChance && !showAmount) text.append("Rain outlook");
        text.append(" rain");
        return text.toString();
    }

    /** First uninterrupted hourly rain signal, never claiming minute-level nowcasting. */
    public static String timing(Forecast f, Instant now) {
        LocalDateTime base;
        try { base = ForecastQuality.hourStart(now, f.timezone); }
        catch (DateTimeException ex) { return "Rain timing unavailable"; }
        if (base == null || f.hours.isEmpty()) return tomorrow(f);
        boolean[] wet = new boolean[48];
        boolean[] known = new boolean[48];
        for (Forecast.Hour hour : f.hours) {
            LocalDateTime at = ForecastQuality.localTime(hour.time);
            if (at == null) continue;
            long offset = ChronoUnit.HOURS.between(base, at);
            if (offset < 0 || offset >= wet.length) continue;
            int i = (int) offset;
            int p = ForecastQuality.probability(hour.rainProbability);
            double mm = ForecastQuality.precipitation(hour.rainMm);
            known[i] = p >= 0 || Double.isFinite(mm);
            wet[i] = (p >= 40) || (Double.isFinite(mm) && mm >= 0.2);
        }
        int first = -1;
        for (int i = 0; i < wet.length; i++) if (wet[i]) { first = i; break; }
        if (first < 0) return tomorrow(f);
        int duration = 0;
        for (int i = first; i < wet.length && known[i] && wet[i]; i++) duration++;
        String onset = first == 0 ? "Rain signal this hour" : "Rain signal in ~" + first + "h";
        return onset + (duration > 0 ? " · ~" + duration + "h window" : "")
                + (f.stale ? " · Older forecast" : f.rainAgreement.isEmpty() ? "" : " · " + f.rainAgreement);
    }

    private static String tomorrow(Forecast f) {
        if (f.days.size() <= 1) return "Hourly rain timing unavailable";
        Forecast.Day d = f.days.get(1);
        if (d.rainProbability >= 0 || Double.isFinite(d.rainMm))
            return "Tomorrow: " + percent(d.rainProbability) + " / " + compactMm(d.rainMm);
        return "Hourly rain timing unavailable";
    }
}
