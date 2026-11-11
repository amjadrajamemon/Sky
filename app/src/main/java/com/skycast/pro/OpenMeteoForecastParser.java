package com.skycast.pro;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Normalize the provider's local timestamps and precipitation interval boundaries. */
public final class OpenMeteoForecastParser {
    private OpenMeteoForecastParser() {}

    public static Forecast parse(JSONObject response, double lat, double lon, String city,
                                 String fallbackTimezone, String modelName, Instant now) throws IOException {
        JSONObject current = response.optJSONObject("current");
        if (current == null) throw new IOException("Missing current forecast");
        double gridLat = response.optDouble("latitude", Double.NaN);
        double gridLon = response.optDouble("longitude", Double.NaN);
        if (!Double.isFinite(gridLat) || !Double.isFinite(gridLon)
                || Math.abs(gridLat) > 90 || Math.abs(gridLon) > 180
                || LocalForecastSkill.distanceKm(lat, lon, gridLat, gridLon) > 80) {
            throw new IOException("Forecast grid is too far from the selected location");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(response.optString("timezone", fallbackTimezone));
        } catch (DateTimeException e) {
            throw new IOException("Invalid provider timezone", e);
        }
        LocalDateTime localNow = LocalDateTime.ofInstant(now, zone);
        LocalDateTime validTime = ForecastQuality.localTime(current.optString("time", ""));
        if (validTime == null || validTime.isAfter(localNow.plusMinutes(15))) {
            throw new IOException("Invalid provider forecast time");
        }
        Forecast f = new Forecast();
        f.source = "Open-Meteo · " + modelName;
        f.city = city;
        f.latitude = lat;
        f.longitude = lon;
        f.timezone = zone.getId();
        f.cached = response.optBoolean("_skycast_cache");
        f.stale = response.optBoolean("_skycast_stale") || validTime.isBefore(localNow.minusHours(2));
        f.fetchedAt = response.optLong("_skycast_fetched_at", now.toEpochMilli());
        f.retrievedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(f.fetchedAt), zone).toString();
        f.status = response.optString("_skycast_error", "");
        f.temperatureC = current.optDouble("temperature_2m", Double.NaN);
        f.feelsLikeC = current.optDouble("apparent_temperature", Double.NaN);
        f.windKmh = current.optDouble("wind_speed_10m", Double.NaN);
        f.humidityPct = current.optDouble("relative_humidity_2m", Double.NaN);
        f.weatherCode = current.optInt("weather_code", -1);
        f.day = current.optInt("is_day", 1) != 0;

        JSONObject hourly = response.optJSONObject("hourly");
        JSONArray times = hourly == null ? null : hourly.optJSONArray("time");
        LocalDateTime firstHour = ForecastQuality.hourStart(now, f.timezone);
        if (times != null) for (int i = 0; i < times.length(); i++) {
            LocalDateTime end = ForecastQuality.localTime(times.optString(i, ""));
            if (end == null) continue;
            LocalDateTime start = end.minusHours(1);
            if (start.isBefore(firstHour) || !start.isBefore(firstHour.plusHours(48))) continue;
            Forecast.Hour hour = new Forecast.Hour();
            hour.time = start.toString();
            hour.weatherCode = integer(hourly, "weather_code", i);
            hour.rainMm = number(hourly, "precipitation", i);
            hour.rainProbability = integer(hourly, "precipitation_probability", i);
            f.hours.add(hour);
        }
        JSONObject daily = response.optJSONObject("daily");
        JSONArray dates = daily == null ? null : daily.optJSONArray("time");
        if (dates != null) for (int i = 0; i < Math.min(7, dates.length()); i++) {
            Forecast.Day day = new Forecast.Day();
            day.date = dates.optString(i, "");
            day.weatherCode = integer(daily, "weather_code", i);
            day.highC = number(daily, "temperature_2m_max", i);
            day.lowC = number(daily, "temperature_2m_min", i);
            day.rainMm = number(daily, "precipitation_sum", i);
            day.rainProbability = integer(daily, "precipitation_probability_max", i);
            day.uvMax = number(daily, "uv_index_max", i);
            f.days.add(day);
        }
        return f;
    }

    private static double number(JSONObject section, String name, int index) {
        JSONArray values = section.optJSONArray(name);
        return values == null || values.isNull(index) ? Double.NaN : values.optDouble(index, Double.NaN);
    }

    private static int integer(JSONObject section, String name, int index) {
        JSONArray values = section.optJSONArray(name);
        return values == null || values.isNull(index) ? -1 : values.optInt(index, -1);
    }
}
