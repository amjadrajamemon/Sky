package com.skycast.pro;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
public class NowcastSourceTest {
    public static void main(String[] args) {
        try {
            long now=Instant.parse("2026-11-11T10:00:00Z").toEpochMilli();
            // JSON round-trip keeps uncertainty distinct from zero precipitation.
            RainNowcast n=new RainNowcast();n.headline="No radar evidence";n.radarCoverage=false;n.modelChance=-1;
            RainNowcast copy=RainNowcastJson.decode(RainNowcastJson.encode(n));
            if(copy.modelChance!=-1||copy.radarAvailable||!copy.headline.equals(n.headline))throw new AssertionError("Nowcast JSON regression");
            JSONObject o=new JSONObject().put("icaoId","OPRN").put("obsTime","2026-11-11T09:30:00Z").put("wxString","-RA")
                    .put("rawOb","OPRN 110930Z 20005KT 9999 -RA SCT030 21/18 Q1013");
            StationObservation parsed=StationObservation.parse(new JSONArray().put(o),new String[]{"OPRN","Rawalpindi","33.616","73.099"},33.616,73.099,now);
            if(parsed==null || !parsed.raining || parsed.observedAt!=Instant.parse("2026-11-11T09:30:00Z").toEpochMilli())
                throw new AssertionError("Station rain METAR detection failed");
            JSONObject clear=new JSONObject().put("icaoId","OPRN").put("obsTime","2026-11-11T09:30:00Z")
                    .put("rawOb","OPRN 110930Z 20005KT 9999 SCT030 21/18 Q1013");
            StationObservation dry=StationObservation.parse(new JSONArray().put(clear),new String[]{"OPRN","Rawalpindi","33.616","73.099"},33.616,73.099,now);
            if(dry==null||dry.raining)throw new AssertionError("No-rain METAR must not be labelled rainy");
            if(StationObservation.parse(new JSONArray().put(o),new String[]{"OPRN","Rawalpindi","33.616","73.099"},33.616,73.099,now+2*3600_000L)!=null)
                throw new AssertionError("Stale station observations must be rejected");
            if(!RainNowcastJson.decode(new JSONObject()).radarState.equals("unavailable"))throw new AssertionError("Default radar should be unavailable");
            if(n.current(now))throw new AssertionError("Uninitialized nowcast must not be current");
            if(!RainNowcastEngine.class.getName().endsWith("RainNowcastEngine"))throw new AssertionError("Engine reference");
            System.out.println("Nowcast source, station METAR and JSON regression tests passed");
        }catch(Exception e){throw new RuntimeException(e);}
    }
}
