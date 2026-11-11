package com.skycast.pro;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Durable last-good snapshot; stale status always computed from time. */
public final class ForecastStore {
    private final AppPrefs prefs;
    public ForecastStore(Context c) {this(new AppPrefs(c));}
    ForecastStore(AppPrefs prefs) {this.prefs=prefs;}
    private static Object safe(double x) { return Double.isFinite(x)?x:JSONObject.NULL; }
    public void save(Forecast f) {
        try {
            JSONObject j=new JSONObject();j.put("source",f.source);j.put("city",f.city);j.put("timezone",f.timezone);
            j.put("lat",safe(f.latitude));j.put("lon",safe(f.longitude));j.put("temp",safe(f.temperatureC));
            j.put("feels",safe(f.feelsLikeC));j.put("wind",safe(f.windKmh));j.put("humidity",safe(f.humidityPct));
            j.put("code",f.weatherCode);j.put("day",f.day);j.put("status",f.status);j.put("rainAgreement",f.rainAgreement);j.put("conditionText",f.conditionText);
            JSONArray days=new JSONArray();for(Forecast.Day d:f.days){JSONObject k=new JSONObject();
                k.put("date",d.date);k.put("code",d.weatherCode);k.put("high",safe(d.highC));k.put("low",safe(d.lowC));
                k.put("mm",safe(d.rainMm));k.put("pop",d.rainProbability);k.put("uv",safe(d.uvMax));days.put(k);}
            JSONArray hours=new JSONArray();for(Forecast.Hour h:f.hours){JSONObject k=new JSONObject();
                k.put("time",h.time);k.put("code",h.weatherCode);k.put("mm",safe(h.rainMm));k.put("pop",h.rainProbability);hours.put(k);}
            j.put("days",days);j.put("hours",hours);j.put("rainHourStarts",true);j.put("stale",f.stale);
            prefs.saveForecast(j.toString(), f.fetchedAt>0?f.fetchedAt:System.currentTimeMillis());
        }catch(Exception ignored){/* Keep previous last-good snapshot on persistence failure. */}
    }
    private static double number(JSONObject o,String name){return o.optDouble(name,Double.NaN);}
    public Forecast load(){
        try {
            String s=prefs.get("lastForecast","");if(s.isEmpty())return null;
            JSONObject j=new JSONObject(s);Forecast f=new Forecast();
            f.source=j.optString("source","");f.city=j.optString("city","");f.timezone=j.optString("timezone","");
            f.latitude=number(j,"lat");f.longitude=number(j,"lon");
            if(prefs.hasLocation() && (Math.abs(f.latitude-prefs.lat())>0.005||Math.abs(f.longitude-prefs.lon())>0.005))return null;
            f.temperatureC=number(j,"temp");
            f.feelsLikeC=number(j,"feels");f.windKmh=number(j,"wind");f.humidityPct=number(j,"humidity");
            f.weatherCode=j.optInt("code",-1);f.day=j.optBoolean("day",true);f.rainAgreement=j.optString("rainAgreement","");f.status=j.optString("status","");f.conditionText=j.optString("conditionText","");
            java.time.ZoneId zone;try{zone=java.time.ZoneId.of(f.timezone);}catch(Exception e){zone=java.time.ZoneId.systemDefault();}
            java.time.LocalDateTime now=java.time.LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0);
            JSONArray days=j.optJSONArray("days");if(days!=null)for(int i=0;i<days.length();i++){
                JSONObject k=days.optJSONObject(i);if(k==null)continue;Forecast.Day d=new Forecast.Day();
                d.date=k.optString("date","");d.weatherCode=k.optInt("code",-1);
                try{if(java.time.LocalDate.parse(d.date).isBefore(now.toLocalDate()))continue;}catch(Exception e){continue;}
                d.highC=number(k,"high");d.lowC=number(k,"low");d.rainMm=number(k,"mm");
                d.rainProbability=k.optInt("pop",-1);d.uvMax=number(k,"uv");f.days.add(d);
            }
            boolean olderHourEndTimes=!j.optBoolean("rainHourStarts") && f.source.contains("Open-Meteo");
            JSONArray hours=j.optJSONArray("hours");if(hours!=null)for(int i=0;i<hours.length();i++){
                JSONObject k=hours.optJSONObject(i);if(k==null)continue;Forecast.Hour h=new Forecast.Hour();
                h.time=k.optString("time","");h.weatherCode=k.optInt("code",-1);
                java.time.LocalDateTime start=ForecastQuality.localTime(h.time);
                if(start==null)continue;
                if(olderHourEndTimes)start=start.minusHours(1);
                if(start.isBefore(now))continue;
                h.time=start.toString();
                h.rainMm=number(k,"mm");h.rainProbability=k.optInt("pop",-1);f.hours.add(h);
            }
            long age=System.currentTimeMillis()-prefs.lastSuccess();
            f.cached=true;f.stale=j.optBoolean("stale")||age<0||age>Math.max(2*3600_000L,2L*prefs.refreshMinutes()*60_000L);
            f.fetchedAt=prefs.lastSuccess();
            f.retrievedAt=java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm",java.util.Locale.getDefault()).withZone(zone).format(java.time.Instant.ofEpochMilli(f.fetchedAt));
            ForecastQuality.prepare(f,java.time.Instant.now());
            return f;
        }catch(Exception ignored){return null;}
    }
}
