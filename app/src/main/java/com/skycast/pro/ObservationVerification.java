package com.skycast.pro;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Nearby-station retrospective verification; station is a proxy, never household ground truth. */
public final class ObservationVerification {
    private ObservationVerification(){}
    public static String observe(AppPrefs prefs,Forecast forecast,StationObservation observation,long now){
        if(observation==null||observation.kilometers>20)return "No sufficiently close station for retrospective checks";
        try {
            String identity=RadarNowcastClient.locationKey(prefs.lat(),prefs.lon())+":"+observation.id;
            if(!identity.equals(prefs.get("rain_station_verification_location",""))){
                prefs.put("rain_station_verification_location",identity);
                prefs.put("rain_station_verification_history","[]");prefs.put("rain_station_verification_scores","{}");
            }
            JSONArray history=new JSONArray(prefs.get("rain_station_verification_history","[]"));
            JSONObject scores=new JSONObject(prefs.get("rain_station_verification_scores","{}"));
            JSONArray remain=new JSONArray();
            for(int i=0;i<history.length();i++){
                JSONObject h=history.optJSONObject(i);if(h==null)continue;
                long target=h.optLong("time");
                if(Math.abs(target-observation.observedAt)<=25*60_000L){
                    boolean predicted=h.optBoolean("wet"),actual=observation.raining;
                    String code=predicted?(actual?"hit":"false"):(actual?"miss":"dry");
                    scores.put(code,scores.optInt(code)+1);
                }else if(target>now-90*60_000L&&target<now+3*3600_000L)remain.put(h);
            }
            // Persist a prediction to verify against a later airport observation; at least 45m ahead.
            LocalDateTime start=ForecastQuality.hourStart(Instant.ofEpochMilli(now).plusSeconds(3600),forecast.timezone);
            for(Forecast.Hour h:forecast.hours){
                LocalDateTime at=ForecastQuality.localTime(h.time);if(at==null||!at.equals(start))continue;
                boolean wet=(h.rainProbability>=60)||(Double.isFinite(h.rainMm)&&h.rainMm>=0.2);
                long target=Instant.ofEpochMilli(now).plusSeconds(3600).toEpochMilli();
                if(remain.length()==0||Math.abs(remain.getJSONObject(remain.length()-1).optLong("time")-target)>20*60_000L)
                    remain.put(new JSONObject().put("time",target).put("wet",wet));
                break;
            }
            JSONArray bounded=new JSONArray();for(int i=Math.max(0,remain.length()-100);i<remain.length();i++)bounded.put(remain.get(i));
            prefs.put("rain_station_verification_history",bounded.toString());
            prefs.put("rain_station_verification_scores",scores.toString());
            int n=scores.optInt("hit")+scores.optInt("false")+scores.optInt("miss")+scores.optInt("dry");
            return n<5?"Station back-checks: "+n+" (insufficient evidence)":
                "Station checks "+n+" · hits "+scores.optInt("hit")+" · false "+scores.optInt("false")
                +" · misses "+scores.optInt("miss");
        }catch(Exception e){return "Station verification temporarily unavailable";}
    }
}
