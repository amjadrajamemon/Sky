package com.skycast.pro;

import java.util.ArrayList;
import java.util.List;

/** Data contract: missing rainfall probability is -1, never inferred from amount. */
public final class Forecast {
    public String source = "";
    public String city = "";
    public String timezone = "";
    public String retrievedAt = "";
    public long fetchedAt;
    public double latitude, longitude;
    public double temperatureC = Double.NaN;
    public double feelsLikeC = Double.NaN;
    public double windKmh = Double.NaN;
    public double humidityPct = Double.NaN;
    public int weatherCode = -1;
    public boolean day = true;
    public boolean cached, stale;
    public String status = "";
    public String conditionText = "";
    /** Optional qualitative cross-model rain signal, not a calibrated accuracy rating. */
    public String rainAgreement = "";
    /** Optional independently timestamped 0–2 h radar/observation nowcast. */
    public RainNowcast nowcast;
    public final List<Hour> hours = new ArrayList<>();
    public final List<Day> days = new ArrayList<>();

    public static final class Day {
        public String date = "";
        public int weatherCode = -1;
        public double highC = Double.NaN;
        public double lowC = Double.NaN;
        public double rainMm = Double.NaN;
        public double uvMax = Double.NaN;
        public int rainProbability = -1;
    }
    public static final class Hour {
        /** Local start of the accumulation period. */
        public String time = "";
        public int weatherCode = -1;
        public int rainProbability = -1;
        public double rainMm = Double.NaN;
    }
}
