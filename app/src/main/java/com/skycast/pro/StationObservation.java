package com.skycast.pro;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

/** Hourly airport reports are observations, not neighborhood-level rain sensors. */
public final class StationObservation {
    public final String id;
    public final String label;
    public final double kilometers;
    public final long observedAt;
    public final boolean raining;
    public final String report;
    private StationObservation(String id,String label,double km,long time,boolean rain,String report){
        this.id=id;this.label=label;this.kilometers=km;this.observedAt=time;this.raining=rain;this.report=report;
    }
    // Well-known ICAO airports: only select within 60 km of saved location.
    private static final String[][] STATIONS={
        {"OPIS","Islamabad","33.549","72.825"},{"OPRN","Rawalpindi","33.616","73.099"},
        {"OPLA","Lahore","31.521","74.403"},{"OPKC","Karachi","24.906","67.161"},
        {"OPPS","Peshawar","33.994","71.514"},{"OPQT","Quetta","30.251","66.938"},
        {"OPMT","Multan","30.203","71.419"},{"OPFA","Faisalabad","31.365","72.995"},
        {"OPST","Sialkot","32.536","74.364"},{"OPSK","Sukkur","27.723","68.792"},
        {"OPNH","Nawabshah","26.219","68.391"},{"OPGD","Gwadar","25.233","62.329"}
    };
    private static final Pattern CURRENT_RAIN=Pattern.compile("(?:^|\\s)[+-]?(?:(?:TS|SH|FZ)?(?:RA|DZ)|(?:RA|DZ)(?:SN|GR)?)(?=\\s|$)");
    static String[] nearest(double lat,double lon){
        double best=Double.MAX_VALUE;String[] station=null;
        for(String[] s:STATIONS){double km=distance(lat,lon,Double.parseDouble(s[2]),Double.parseDouble(s[3]));
            if(km<best){best=km;station=s;}}
        return best<=60?station:null;
    }
    public static double distance(double lat,double lon,double lat2,double lon2){
        double a=Math.sin(Math.toRadians(lat2-lat)/2),b=Math.sin(Math.toRadians(lon2-lon)/2);
        double h=a*a+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(lat2))*b*b;
        return 12742*Math.asin(Math.min(1,Math.sqrt(h)));
    }
    static StationObservation parse(JSONArray observations,String[] station,double latitude,double longitude,long now){
        if(observations==null||station==null)return null;
        for(int i=0;i<observations.length();i++){
            JSONObject o=observations.optJSONObject(i);if(o==null||!station[0].equalsIgnoreCase(o.optString("icaoId",o.optString("stationId",""))))continue;
            long at=timestamp(o.opt("obsTime"));
            if(at<=0||at>now+2*60_000L||now-at>105*60_000L)continue;
            String weather=o.optString("wxString","");String raw=o.optString("rawOb","");
            boolean rain=CURRENT_RAIN.matcher(weather).find()||CURRENT_RAIN.matcher(raw).find();
            double km=distance(latitude,longitude,Double.parseDouble(station[2]),Double.parseDouble(station[3]));
            return new StationObservation(station[0],station[1],km,at,rain,raw);
        }
        return null;
    }
    static long timestamp(Object raw){
        try{
            if(raw instanceof Number){long s=((Number)raw).longValue();return s>1_000_000_000_000L?s:s*1000L;}
            String text=String.valueOf(raw);if(text.matches("[0-9]{10,13}")){long s=Long.parseLong(text);return s>1_000_000_000_000L?s:s*1000L;}
            return Instant.parse(text).toEpochMilli();
        }catch(Exception e){return 0;}
    }
    static StationObservation fetch(double lat,double lon,long now)throws Exception{
        String[] station=nearest(lat,lon);if(station==null)return null;
        // Never silently move the target location to the airport; report distance.
        String uri="https://aviationweather.gov/api/data/metar?ids="+station[0]+"&format=json&hours=2";
        byte[] response=readNoaa(uri);
        return parse(new JSONArray(new String(response,StandardCharsets.UTF_8)),station,lat,lon,now);
    }
    private static byte[] readNoaa(String uri)throws Exception{
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(uri).openConnection();
        c.setConnectTimeout(4200);c.setReadTimeout(5200);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","SkyCastProNext/2.0 (https://github.com/amjadrajamemon/Sky)");
        try{
            if(c.getResponseCode()!=200)throw new java.io.IOException("Observation HTTP "+c.getResponseCode());
            try(java.io.InputStream in=c.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>120_000)throw new java.io.IOException("Oversize observations");}
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
    public String summary(){
        return label+" airport ("+Math.round(kilometers)+" km): "+(raining?"rain reported":"no rain reported at station");
    }
}
