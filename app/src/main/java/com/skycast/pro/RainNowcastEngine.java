package com.skycast.pro;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Fusion avoids false precision: radar velocity only with verified coverage & coherent frames. */
public final class RainNowcastEngine {
    private RainNowcastEngine(){}
    public static void modelSignal(RainNowcast result, Forecast forecast, Instant now){
        LocalDateTime start;
        try {start=ForecastQuality.hourStart(now,forecast.timezone);}catch(Exception e){return;}
        int p=-1,seen=0;double mm=0;
        for(Forecast.Hour h:forecast.hours){
            LocalDateTime time=ForecastQuality.localTime(h.time);
            if(time==null)continue;
            long offset=ChronoUnit.HOURS.between(start,time);
            if(offset<0||offset>=2)continue;
            if(h.rainProbability>=0)p=Math.max(p,h.rainProbability);
            if(Double.isFinite(h.rainMm)&&h.rainMm>=0){seen++;mm+=h.rainMm;}
        }
        result.modelChance=p;result.modelMm=seen>0?mm:Double.NaN;
        result.evidence="Model forecast only";
        result.headline="Next 2h: "+(p>=0?p+"% peak hourly chance":"rain chance unavailable");
        result.detail=(seen>0?String.format(java.util.Locale.US,"%.1f mm model forecast · ",mm):"")+"Hourly model timing; radar not verified";
    }
    public static void radarSignal(RainNowcast out,RadarMotion.Motion motion){
        if(!out.radarCoverage||!out.radarAvailable||motion==null||!motion.coherent)return;
        out.radarMoving=true;
        System.arraycopy(motion.steps,0,out.bins,0,Math.min(out.bins.length,motion.steps.length));
        int first=-1,last=-1,peak=0;
        for(int i=0;i<out.bins.length;i++)if(out.bins[i]>0){if(first<0)first=i;last=i;peak=Math.max(peak,out.bins[i]);}
        out.intensity=RadarMotion.intensity(peak);
        if(first<0){
            out.evidence="Radar motion estimated · no projected echoes near target";
            out.headline="No radar echoes projected nearby";
            out.detail="Not a guarantee of dry weather · model "+modelLabel(out);
            return;
        }
        int onset=first*10;
        int end=Math.min(120,(last+1)*10);
        out.arrivalStart=Math.max(0,onset-15);
        out.arrivalEnd=Math.min(120,onset+15);
        out.duration=Math.max(10,end-onset);
        String timing=first==0?"Radar echoes near you now":"Possible rain in "+out.arrivalStart+"–"+out.arrivalEnd+" min";
        out.headline=timing;
        out.evidence="Two radar frames · motion model, not measured rain";
        out.detail=out.intensity+" · ~"+out.duration+" min signal · "+modelLabel(out);
        if(onset>60)out.evidence+=" · 60–120 min extrapolation has lower confidence";
    }
    private static String modelLabel(RainNowcast n){return n.modelChance>=0?n.modelChance+"% model rain chance":"model probability unavailable";}
    public static void finalizeEvidence(RainNowcast out,long now){
        if(out.observedRain && out.observationTime>0 && now-out.observationTime<90*60_000L){
            out.evidence += " · nearby station reports rain";
        }
        if(!out.validRadar(now)){
            out.radarMoving=false;out.radarAvailable=false;
            out.radarState="Radar missing, unverified or old";
            out.evidence="Model/observation only";
            out.arrivalStart=-1;out.arrivalEnd=-1;out.duration=-1;
        }
    }
}
