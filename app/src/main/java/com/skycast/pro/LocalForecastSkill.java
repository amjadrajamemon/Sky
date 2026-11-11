package com.skycast.pro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** OPIS verification in tools/verify_islamabad.py covers fixed 24 and 48 hour temperature leads. */
public final class LocalForecastSkill {
    public static final String MODEL = "ecmwf_ifs";
    private static final LocalDate EVALUATION_END = LocalDate.of(2026, 11, 10);
    private static final double STATION_LATITUDE = 33.5491;
    private static final double STATION_LONGITUDE = 72.8256;
    private static final double RADIUS_KM = 35;

    private LocalForecastSkill() {}

    public static boolean applies(double lat, double lon, Instant now) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return false;
        }
        LocalDate day = now.atOffset(ZoneOffset.UTC).toLocalDate();
        if (!day.isAfter(EVALUATION_END) || day.isAfter(EVALUATION_END.plusDays(90))) {
            return false;
        }
        return distanceKm(lat, lon, STATION_LATITUDE, STATION_LONGITUDE) <= RADIUS_KM;
    }

    public static double distanceKm(double lat, double lon, double otherLat, double otherLon) {
        double latitudeDelta = Math.toRadians(otherLat - lat) / 2;
        double longitudeDelta = Math.toRadians(otherLon - lon) / 2;
        double distance = Math.pow(Math.sin(latitudeDelta), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(otherLat))
                * Math.pow(Math.sin(longitudeDelta), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, distance)));
    }

    public static String evidence() {
        return "Islamabad Airport temperature benchmark · 24 h error 1.28°C across 621 matched hours "
                + "(16 Oct–10 Nov 2026).";
    }
}
