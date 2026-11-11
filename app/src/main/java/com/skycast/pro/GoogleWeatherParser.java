package com.skycast.pro;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Google WeatherNext 2 ensemble mean delivered by Open-Meteo, not Google Weather UI data. */
public final class GoogleWeatherParser {
    private GoogleWeatherParser() {}

    public static Forecast parse(JSONObject json, double lat, double lon, String city,
                                 String fallbackTimezone, Instant now) throws IOException {
        if (json.optBoolean("error")) throw new IOException("Google model: " + json.optString("reason", "provider error"));
        JSONObject hourly = json.optJSONObject("hourly");
        JSONArray times = hourly == null ? null : hourly.optJSONArray("time");
        if (times == null || times.length() == 0) throw new IOException("Google model has no forecast hours");
        if (hourly.has("temperature_2m_member01")) throw new IOException("Expected Google ensemble mean, received individual members");
        ZoneId zone;
        try { zone = ZoneId.of(json.optString("timezone", fallbackTimezone)); }
        catch (Exception e) { throw new IOException("Google model timezone unavailable", e); }
        LocalDateTime localNow = LocalDateTime.ofInstant(now, zone);
        LocalDateTime firstHour = localNow.withMinute(0).withSecond(0).withNano(0);
        int current = -1;
        long nearestSeconds = 7200;
        for (int i = 0; i < times.length(); i++) {
            LocalDateTime t = time(times.optString(i, ""));
            if (t == null || !Double.isFinite(number(hourly, "temperature_2m", i))) continue;
            long age = Duration.between(t, localNow).getSeconds();
            if (age >= 0 && age < nearestSeconds) { nearestSeconds = age; current = i; }
        }
        if (current < 0) throw new IOException("Google model has no current forecast; trying backup");
        Forecast f = new Forecast();
        f.source = "Google WeatherNext 2 · Open-Meteo ensemble mean";
        f.latitude = lat; f.longitude = lon; f.city = city; f.timezone = zone.getId();
        f.cached = json.optBoolean("_skycast_cache"); f.stale = json.optBoolean("_skycast_stale");
        f.fetchedAt = json.optLong("_skycast_fetched_at", now.toEpochMilli());
        f.retrievedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(f.fetchedAt), zone).toString();
        f.temperatureC = number(hourly, "temperature_2m", current);
        f.windKmh = number(hourly, "wind_speed_10m", current);
        f.weatherCode = integer(hourly, "weather_code", current, -1);
        f.day = integer(hourly, "is_day", current, localNow.getHour() >= 6 && localNow.getHour() < 18 ? 1 : 0) != 0;
        // This feed does not supply surface humidity, feels-like, or precipitation probabilities.
        f.status = "Google DeepMind model via Open-Meteo; distinct from Google Weather's consumer forecast. "
            + "Ensemble mean; hourly values interpolate 6-hour model steps. Rain chance unavailable; current conditions are model estimates.";
        if (json.has("_skycast_error")) f.status += " " + json.optString("_skycast_error");
        for (int i = 0; i < times.length(); i++) {
            LocalDateTime end = time(times.optString(i, ""));
            if (end == null) continue;
            LocalDateTime start = end.minusHours(1);
            if (start.isBefore(firstHour) || !start.isBefore(firstHour.plusHours(48))) continue;
            Forecast.Hour h = new Forecast.Hour(); h.time = start.toString();
            h.weatherCode = integer(hourly, "weather_code", i, -1);
            h.rainMm = number(hourly, "precipitation", i);
            f.hours.add(h);
        }
        JSONObject daily = json.optJSONObject("daily");
        JSONArray dates = daily == null ? null : daily.optJSONArray("time");
        if (dates != null) for (int i = 0; i < dates.length() && f.days.size() < 7; i++) {
            String date = dates.optString(i, "");
            LocalDate day;
            try { day = LocalDate.parse(date); } catch (Exception e) { continue; }
            if (day.isBefore(localNow.toLocalDate())) continue;
            Forecast.Day d = new Forecast.Day(); d.date = date;
            d.highC = number(daily, "temperature_2m_max", i); d.lowC = number(daily, "temperature_2m_min", i);
            if (!Double.isFinite(d.highC) || !Double.isFinite(d.lowC)) continue;
            d.rainMm = number(daily, "precipitation_sum", i);
            // A representative midday icon, not an invented daily probability or averaged WMO code.
            for (int j = 0; j < times.length(); j++) {
                LocalDateTime t = time(times.optString(j, ""));
                if (t != null && t.toLocalDate().equals(day) && t.getHour() == 12) {
                    d.weatherCode = integer(hourly, "weather_code", j, -1); break;
                }
            }
            f.days.add(d);
        }
        if (f.hours.isEmpty() || f.days.isEmpty()) throw new IOException("Google model forecast incomplete; trying backup");
        return f;
    }

    private static LocalDateTime time(String s) {
        try { return LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }
    private static double number(JSONObject o, String key, int i) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.isNull(i) ? Double.NaN : a.optDouble(i, Double.NaN);
    }
    private static int integer(JSONObject o, String key, int i, int fallback) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.isNull(i) ? fallback : a.optInt(i, fallback);
    }
}
