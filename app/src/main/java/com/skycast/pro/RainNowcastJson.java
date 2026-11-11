package com.skycast.pro;

import org.json.JSONArray;
import org.json.JSONObject;

/** Serialization isolated from the pure-Java nowcast engine. */
public final class RainNowcastJson {
    private RainNowcastJson(){}
    private static Object safe(double value){return Double.isFinite(value)?value:JSONObject.NULL;}
    public static JSONObject encode(RainNowcast n){
        JSONObject o=new JSONObject();if(n==null)return o;
        try {
            o.put("updated",n.updatedAt).put("radarTime",n.radarTime).put("observationTime",n.observationTime)
             .put("radarState",n.radarState).put("observation",n.observation).put("headline",n.headline)
             .put("detail",n.detail).put("intensity",n.intensity).put("evidence",n.evidence)
             .put("verification",n.verification).put("station",n.station).put("arrivalStart",n.arrivalStart)
             .put("arrivalEnd",n.arrivalEnd).put("duration",n.duration).put("modelChance",n.modelChance)
             .put("modelMm",safe(n.modelMm)).put("radarCoverage",n.radarCoverage)
             .put("radarAvailable",n.radarAvailable).put("radarMoving",n.radarMoving)
             .put("observedRain",n.observedRain);
            JSONArray a=new JSONArray();for(int value:n.bins)a.put(value);o.put("bins",a);
        }catch(Exception ignored){}return o;
    }
    public static RainNowcast decode(JSONObject o){
        if(o==null)return null;RainNowcast n=new RainNowcast();
        n.updatedAt=o.optLong("updated");n.radarTime=o.optLong("radarTime");n.observationTime=o.optLong("observationTime");
        n.radarState=o.optString("radarState",n.radarState);n.observation=o.optString("observation",n.observation);
        n.headline=o.optString("headline",n.headline);n.detail=o.optString("detail",n.detail);
        n.intensity=o.optString("intensity",n.intensity);n.evidence=o.optString("evidence",n.evidence);
        n.verification=o.optString("verification",n.verification);n.station=o.optString("station","");
        n.arrivalStart=o.optInt("arrivalStart",-1);n.arrivalEnd=o.optInt("arrivalEnd",-1);n.duration=o.optInt("duration",-1);
        n.modelChance=o.optInt("modelChance",-1);n.modelMm=o.optDouble("modelMm",Double.NaN);
        n.radarCoverage=o.optBoolean("radarCoverage");n.radarAvailable=o.optBoolean("radarAvailable");
        n.radarMoving=o.optBoolean("radarMoving");n.observedRain=o.optBoolean("observedRain");
        JSONArray a=o.optJSONArray("bins");if(a!=null)for(int i=0;i<Math.min(n.bins.length,a.length());i++)n.bins[i]=a.optInt(i,-1);
        return n;
    }
}
