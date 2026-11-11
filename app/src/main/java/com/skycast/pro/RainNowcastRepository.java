package com.skycast.pro;

import android.content.Context;
import org.json.JSONObject;
import java.time.Instant;

/** Multi-source nowcast cache, with strict separation of observations and model forecasts. */
public final class RainNowcastRepository {
    private final Context context;
    private final AppPrefs prefs;
    public RainNowcastRepository(Context context,AppPrefs prefs){this.context=context.getApplicationContext();this.prefs=prefs;}
    public RainNowcast cached(){
        try {
            if(!prefs.hasLocation())return null;
            if(!RadarNowcastClient.locationKey(prefs.lat(),prefs.lon()).equals(prefs.get("rain_nowcast_location","")))return null;
            return RainNowcastJson.decode(new JSONObject(prefs.get("rain_nowcast_json","{}")));
        }catch(Exception ignored){return null;}
    }
    public RainNowcast update(Forecast forecast,boolean force){
        long now=System.currentTimeMillis();
        RainNowcast previous=cached();
        // Cache radar APIs even if someone repeatedly taps refresh; avoid hammering the free service.
        if(previous!=null && previous.updatedAt>0 && now-previous.updatedAt<10*60_000L)return previous;
        RainNowcast result=new RainNowcast();result.updatedAt=now;
        RainNowcastEngine.modelSignal(result,forecast,Instant.ofEpochMilli(now));
        if(!WeatherCoordinator.isOnline(context)){
            result.radarState="Offline · radar not updated";
            return remember(previous!=null?previous:result);
        }
        // Airport report is evidence for a nearby station, not a measured neighborhood reading.
        String stationVerification="";
        try{
            StationObservation obs=StationObservation.fetch(prefs.lat(),prefs.lon(),now);
            if(obs!=null){result.observation=obs.summary();result.station=obs.id;
                result.observationTime=obs.observedAt;result.observedRain=obs.raining;
                stationVerification=ObservationVerification.observe(prefs,forecast,obs,now);}
            else result.observation="No fresh airport observation within 60 km";
        }catch(Exception ignored){result.observation="Station observations unavailable";}
        if(prefs.radarEnabled()){
            RadarNowcastClient.Result radar=new RadarNowcastClient(context,prefs).fetch(prefs.lat(),prefs.lon(),now);
            result.radarCoverage=radar.coverage;result.radarAvailable=radar.available;result.radarTime=radar.latest;
            result.radarState=radar.reason;
            if(radar.available && radar.motion!=null){
                RainNowcastEngine.radarSignal(result,radar.motion);
                result.verification=NowcastVerification.evaluate(prefs,
                    RadarNowcastClient.locationKey(prefs.lat(),prefs.lon()),radar.latest,radar.motion);
            }
        }else result.radarState="Radar disabled in app settings";
        RainNowcastEngine.finalizeEvidence(result,now);
        if(!stationVerification.isEmpty())result.verification += " · "+stationVerification;
        if(result.observedRain&&!result.radarMoving){
            result.evidence="Nearby airport reports rain · local arrival uncertain";
            result.detail=result.observation+" · "+result.detail;
        }
        return remember(result);
    }
    private RainNowcast remember(RainNowcast value){
        prefs.put("rain_nowcast_location",RadarNowcastClient.locationKey(prefs.lat(),prefs.lon()));
        prefs.put("rain_nowcast_json",RainNowcastJson.encode(value).toString());
        return value;
    }
}
