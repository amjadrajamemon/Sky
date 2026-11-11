package com.skycast.pro;

import org.json.JSONArray;
import org.json.JSONObject;

/** Retrospective radar echo verification, never represented as measured rainfall accuracy. */
public final class NowcastVerification {
    private NowcastVerification(){}
    static final String RECORDS="rain_nowcast_verify_records",SCORES="rain_nowcast_verify_score";
    public static String evaluate(AppPrefs prefs,String location,long radarFrameTime,RadarMotion.Motion motion){
        if(radarFrameTime<=0||motion==null)return "Verification not available";
        try{
            if(!location.equals(prefs.get("rain_verification_location",""))){
                prefs.put(RECORDS,"[]");prefs.put(SCORES,"{}");prefs.put("rain_verification_location",location);
            }
            JSONArray records=new JSONArray(prefs.get(RECORDS,"[]"));JSONObject score=new JSONObject(prefs.get(SCORES,"{}"));
            JSONArray keep=new JSONArray();int checked=0;
            for(int i=0;i<records.length();i++){
                JSONObject r=records.optJSONObject(i);if(r==null)continue;
                long target=r.optLong("time");
                // only verify if target time aligns within one radar-frame interval.
                if(Math.abs(target-radarFrameTime)<=8*60_000L && motion.steps[0]>=0){
                    boolean wet=r.optBoolean("wet"),actual=motion.steps[0]>0;
                    String key=wet?(actual?"hit":"falseAlarm"):(actual?"miss":"dryCorrect");
                    score.put(key,score.optInt(key)+1);checked++;
                }else if(target>radarFrameTime-20*60_000L && target<radarFrameTime+4*3600_000L){keep.put(r);}
            }
            // Only coherent radar motion can generate verification subjects.
            if(motion.coherent)for(int s:new int[]{3,6,9,12}){
                long target=radarFrameTime+s*10*60_000L;
                JSONObject r=new JSONObject().put("time",target).put("wet",motion.steps[s]>0);
                keep.put(r);
            }
            JSONArray bounded=new JSONArray();for(int i=Math.max(0,keep.length()-100);i<keep.length();i++)bounded.put(keep.get(i));
            prefs.put(RECORDS,bounded.toString());prefs.put(SCORES,score.toString());
            int total=score.optInt("hit")+score.optInt("falseAlarm")+score.optInt("miss")+score.optInt("dryCorrect");
            return total<6?"Radar retrospective checks: "+total+" (more needed)":
                "Radar checks "+total+" · hit "+score.optInt("hit")+" · false alarm "+score.optInt("falseAlarm")
                +" · missed "+score.optInt("miss");
        }catch(Exception ignored){return "Radar verification unavailable";}
    }
}
