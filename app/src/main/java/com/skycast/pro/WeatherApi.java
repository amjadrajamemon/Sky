package com.skycast.pro;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keyless data access. All network calls MUST be executed off the UI thread. */
public final class WeatherApi {
    private final AppPrefs prefs;
    private static final class ProviderHttpException extends java.io.IOException {
        final int status;
        ProviderHttpException(int status) {
            super("Weather provider HTTP " + status);
            this.status = status;
        }
    }
    public static final String GOOGLE_MODEL = "google_weathernext2_ensemble_mean";
    public static final String[] MODEL_LABELS = {"Smart Auto", "Open-Meteo Best Match", "ECMWF IFS", "NOAA GFS", "DWD ICON", "Google WeatherNext 2 (via Open-Meteo)", "MSN (public Foreca feed)", "AccuWeather (public page)", "MET Norway"};
    public static final String[] MODEL_IDS = {"smart", "best_match", "ecmwf_ifs", "ncep_gfs_seamless", "icon_seamless", GOOGLE_MODEL, "msn_public", "accu_public", "metno"};
    public static final String[] COMPARE_IDS = {"best_match", "ecmwf_ifs", "ncep_gfs_seamless", "icon_seamless", GOOGLE_MODEL, "msn_public", "accu_public", "metno"};

    public WeatherApi(Context context) { this(new AppPrefs(context)); }
    WeatherApi(AppPrefs prefs) { this.prefs=prefs; }
    public static String modelLabel(String id) {
        for (int i=0;i<MODEL_IDS.length;i++) if(MODEL_IDS[i].equals(id))return MODEL_LABELS[i];
        return "Open-Meteo Best Match";
    }
    private static String getText(InputStream in) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while ((n=in.read(buf))!=-1) {
            out.write(buf,0,n);
            if(out.size()>2_000_000)throw new java.io.IOException("Unexpectedly large weather response");
        }
        return out.toString("UTF-8");
    }
    private static String hash(String input) throws Exception {
        byte[] dig=java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();for(int i=0;i<12;i++)sb.append(String.format(Locale.US,"%02x",dig[i]));return sb.toString();
    }
    private JSONObject cachedGet(String url,long minIntervalMs,long allowStaleMs,boolean force) throws Exception {
        String key="http_"+hash(url);long age=System.currentTimeMillis()-prefs.getLong(key+"_time");
        String saved=prefs.get(key+"_body", "");
        long expiry=prefs.getLong(key+"_expires");
        if (!force && !saved.isEmpty() && age>=0 && age<=allowStaleMs
                && (age<minIntervalMs || System.currentTimeMillis()<expiry)) {
            JSONObject j=new JSONObject(saved);j.put("_skycast_cache",true);j.put("_skycast_stale",false);j.put("_skycast_fetched_at",prefs.getLong(key+"_time"));return j;
        }
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);
            c.setRequestProperty("Accept","application/json");
            // MET Norway requires a distinct, contactable client identity (not generic Dalvik).
            c.setRequestProperty("User-Agent","SkyCastProNext/2.0 (https://github.com/amjadrajamemon)");
            String etag=prefs.get(key+"_etag","");if(!etag.isEmpty())c.setRequestProperty("If-None-Match",etag);
            String last=prefs.get(key+"_last","");if(!last.isEmpty())c.setRequestProperty("If-Modified-Since",last);
            int status=c.getResponseCode();
            if(status==304 && !saved.isEmpty()) {
                prefs.putLong(key+"_expires",System.currentTimeMillis()+minIntervalMs);
                JSONObject j=new JSONObject(saved);j.put("_skycast_cache",true);
                j.put("_skycast_stale",age<0||age>allowStaleMs);
                j.put("_skycast_fetched_at",prefs.getLong(key+"_time"));return j;
            }
            if(status<200 || status>=300) throw new ProviderHttpException(status);
            String body;
            try(InputStream in=c.getInputStream()) { body=getText(in); }
            JSONObject result=new JSONObject(body);
            if(result.optBoolean("error",false))throw new java.io.IOException(result.optString("reason","Weather provider error"));
            long now=System.currentTimeMillis();
            prefs.put(key+"_body",body);prefs.putLong(key+"_time",now);
            String et=c.getHeaderField("ETag");if(et!=null)prefs.put(key+"_etag",et);
            String lm=c.getHeaderField("Last-Modified");if(lm!=null)prefs.put(key+"_last",lm);
            long exp=c.getHeaderFieldDate("Expires",0);
            prefs.putLong(key+"_expires",exp>now?exp:now+minIntervalMs);
            result.put("_skycast_fetched_at",now);return result;
        } catch(Exception err) {
            if(!saved.isEmpty() && age>=0 && age<=allowStaleMs) {
                JSONObject j=new JSONObject(saved);j.put("_skycast_cache",true);j.put("_skycast_stale",true);
                j.put("_skycast_error",err.getMessage());j.put("_skycast_fetched_at",prefs.getLong(key+"_time"));return j;
            }
            throw err;
        } finally { if(c!=null)c.disconnect(); }
    }
    private static String geo(double lat,double lon) {
        return String.format(Locale.US,"latitude=%.4f&longitude=%.4f",lat,lon);
    }
    public Forecast fetchModel(double lat,double lon,String model,boolean force) throws Exception {
        if (GOOGLE_MODEL.equals(model)) return fetchGoogleWeatherNext(lat,lon,force);
        if ("msn_public".equals(model)||"accu_public".equals(model)) return fetchPublicForecast(lat,lon,model,force);
        if ("metno".equals(model)) return fetchMetNorway(lat,lon,force);
        return fetchOpenMeteo(lat,lon,"smart".equals(model)?"best_match":model,force);
    }
    private Forecast fetchPublicForecast(double lat,double lon,String model,boolean force) throws Exception {
        String coordinates=String.format(Locale.US,"%.4f,%.4f",lat,lon);
        boolean msn="msn_public".equals(model);
        String url=msn?"https://weather.service.msn.com/find.aspx?src=outlook&weadegreetype=C&culture=en-US&weasearchstr="+URLEncoder.encode(coordinates,"UTF-8")
            :"https://www.accuweather.com/en/search-locations?query="+URLEncoder.encode(coordinates,"UTF-8");
        JSONObject response=cachedDocument(url,force);String content=response.getString("content");
        Forecast f=msn?PublicWeatherParser.msn(content,lat,lon,prefs.city(),prefs.timezone(),Instant.now())
            :PublicWeatherParser.accuWeather(content,lat,lon,prefs.city(),prefs.timezone(),Instant.now());
        f.cached=response.optBoolean("cached");f.stale=response.optBoolean("stale");f.fetchedAt=response.getLong("time");
        f.retrievedAt=Instant.ofEpochMilli(response.getLong("time")).atZone(ZoneId.of(f.timezone)).toLocalDateTime().toString();
        if(f.stale)f.status+=" Cached provider page; network refresh failed.";
        return f;
    }
    private JSONObject cachedDocument(String url,boolean force) throws Exception {
        String key="public_"+hash(url);long now=System.currentTimeMillis();long savedAt=prefs.getLong(key+"_time");
        String saved=prefs.get(key+"_body","");long age=now-savedAt;
        if(!force&&!saved.isEmpty()&&age>=0&&age<3600_000L)return document(saved,savedAt,true,false);
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent","SkyCastProNext/2.0 (https://github.com/amjadrajamemon)");
            c.setRequestProperty("Accept-Language","en-US,en;q=0.9");c.setRequestProperty("Accept","text/html,application/xml,text/xml;q=0.9");
            String etag=prefs.get(key+"_etag","");if(!etag.isEmpty())c.setRequestProperty("If-None-Match",etag);
            int status=c.getResponseCode();
            if(status==304&&!saved.isEmpty()){
                return document(saved,savedAt,true,age<0||age>6*3600_000L);
            }
            if(status<200||status>=300)throw new java.io.IOException("Public weather source HTTP "+status);
            String body;try(InputStream in=c.getInputStream()){body=getText(in);}
            if(body.trim().isEmpty())throw new java.io.IOException("Public weather source returned no content");
            prefs.put(key+"_body",body);prefs.putLong(key+"_time",now);
            String et=c.getHeaderField("ETag");if(et!=null)prefs.put(key+"_etag",et);
            return document(body,now,false,false);
        }catch(Exception e){
            if(!saved.isEmpty()&&age>=0&&age<6*3600_000L)return document(saved,savedAt,true,true);
            throw e;
        }finally{if(c!=null)c.disconnect();}
    }
    private static JSONObject document(String content,long time,boolean cached,boolean stale) throws Exception {
        return new JSONObject().put("content",content).put("time",time).put("cached",cached).put("stale",stale);
    }
    public Forecast fetchGoogleWeatherNext(double lat,double lon,boolean force) throws Exception {
        String url="https://ensemble-api.open-meteo.com/v1/ensemble?"+geo(lat,lon)
            +"&models="+GOOGLE_MODEL+"&hourly=temperature_2m,precipitation,weather_code,wind_speed_10m,is_day"
            +"&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&forecast_days=7&timezone=auto";
        JSONObject json=cachedGet(url,60*60_000L,6*3600_000L,force);
        return GoogleWeatherParser.parse(json,lat,lon,prefs.city(),prefs.timezone(),Instant.now());
    }
    public Forecast fetchOpenMeteo(double lat,double lon,String model,boolean force) throws Exception {
        String core="https://api.open-meteo.com/v1/forecast?"+geo(lat,lon)+"&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m"
          +"&hourly=precipitation_probability,precipitation,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,uv_index_max"
          +"&timezone=auto&forecast_days=7&models=" + model;
        JSONObject json;
        try { json=cachedGet(core,30*60_000L,6*3600_000L,force); }
        catch(ProviderHttpException e) {
            if (e.status != 400) throw e;
            // Secondary query omits variables which some model-specific feeds may not expose.
            String simpler="https://api.open-meteo.com/v1/forecast?"+geo(lat,lon)
             +"&current=temperature_2m,is_day,weather_code&hourly=precipitation,weather_code"
             +"&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto&forecast_days=7&models="+model;
            json=cachedGet(simpler,30*60_000L,6*3600_000L,force);
        }
        return OpenMeteoForecastParser.parse(json, lat, lon, prefs.city(), prefs.timezone(),
                modelLabel(model), Instant.now());
    }

    public Forecast fetchMetNorway(double lat,double lon,boolean force) throws Exception {
        String url=String.format(Locale.US,"https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",lat,lon);
        JSONObject result=cachedGet(url,180*60_000L,12*3600_000L,false);
        Forecast f=new Forecast();f.source="MET Norway · Locationforecast";f.latitude=lat;f.longitude=lon;
        f.city=prefs.city();f.timezone=prefs.timezone();f.cached=result.optBoolean("_skycast_cache");
        f.stale=result.optBoolean("_skycast_stale");f.status=result.optString("_skycast_error","");
        f.fetchedAt=result.optLong("_skycast_fetched_at",System.currentTimeMillis());
        f.retrievedAt=Instant.ofEpochMilli(f.fetchedAt).atZone(ZoneId.of(f.timezone)).toLocalDateTime().toString();
        JSONObject props=result.optJSONObject("properties");
        JSONArray series=props==null?null:props.optJSONArray("timeseries");
        if(series==null||series.length()==0) throw new java.io.IOException("MET Norway missing forecast hours");
        ZoneId zone;try { zone=ZoneId.of(f.timezone); } catch(Exception err) {zone=ZoneId.systemDefault();}
        int added=0;java.time.LocalDate today=Instant.now().atZone(zone).toLocalDate();
        for(int i=0;i<series.length();i++) {
            JSONObject step=series.optJSONObject(i);if(step==null)continue;
            String when=step.optString("time","");
            ZonedDateTime local;
            try {local=Instant.parse(when).atZone(zone);}catch(Exception err){continue;}
            if(local.toInstant().isBefore(Instant.now().minusSeconds(1800)))continue;
            JSONObject data=step.optJSONObject("data");if(data==null)continue;
            JSONObject instant=data.optJSONObject("instant");
            JSONObject details=instant==null?null:instant.optJSONObject("details");if(details==null)continue;
            JSONObject one=data.optJSONObject("next_1_hours");
            JSONObject next6=data.optJSONObject("next_6_hours");
            JSONObject oneDetails=one==null?null:one.optJSONObject("details");
            JSONObject summary=one==null?null:one.optJSONObject("summary");
            if(summary==null && next6!=null)summary=next6.optJSONObject("summary");
            Forecast.Hour h=new Forecast.Hour();h.time=local.toLocalDateTime().toString();
            h.rainMm=oneDetails==null?Double.NaN:oneDetails.optDouble("precipitation_amount",Double.NaN);
            h.rainProbability=-1;h.weatherCode=metCode(summary==null?"":summary.optString("symbol_code",""));
            if(added==0) {
                f.temperatureC=details.optDouble("air_temperature",Double.NaN);
                f.humidityPct=details.optDouble("relative_humidity",Double.NaN);
                double wind=details.optDouble("wind_speed",Double.NaN);
                f.windKmh=Double.isFinite(wind)?wind*3.6:Double.NaN;
                f.weatherCode=h.weatherCode;int hour=local.getHour();f.day=hour>=6 && hour<19;
            }
            if(added<48 && local.toLocalDateTime().isBefore(java.time.LocalDateTime.now(zone).plusHours(48))) {
                f.hours.add(h);added++;
            }
            Forecast.Day day=null;
            String date=local.toLocalDate().toString();
            for(Forecast.Day d:f.days)if(d.date.equals(date)) {day=d;break;}
            if(day==null && f.days.size()<7 && !local.toLocalDate().isBefore(today)) {day=new Forecast.Day();day.date=date;f.days.add(day);}
            if(day!=null) {
                double t=details.optDouble("air_temperature",Double.NaN);
                if(Double.isFinite(t)) {
                    if(!Double.isFinite(day.highC)||t>day.highC)day.highC=t;
                    if(!Double.isFinite(day.lowC)||t<day.lowC)day.lowC=t;
                }
                if(day.weatherCode<0)day.weatherCode=h.weatherCode;
                if(Double.isFinite(h.rainMm))day.rainMm=(Double.isFinite(day.rainMm)?day.rainMm:0)+h.rainMm;
            }
        }
        if(f.hours.isEmpty())throw new java.io.IOException("MET Norway forecast expired");
        f.status="Rain chance unavailable; daily highs/lows and rainfall are estimates from available MET forecast steps."
            + (prefs.knownTimezone()?"":" Location timezone unverified; displaying times in phone timezone.");
        return f;
    }
    private static int metCode(String symbol){
        if(symbol.contains("thunder"))return 95;
        if(symbol.contains("snow"))return 73;
        if(symbol.contains("rain")||symbol.contains("sleet"))return 63;
        if(symbol.contains("fog"))return 45;
        if(symbol.contains("cloudy") && !symbol.contains("partly"))return 3;
        if(symbol.contains("partly"))return 2;
        return symbol.contains("clear")||symbol.contains("fair")?0:-1;
    }
    public static final class Place {
        public String name; public double lat,lon; public String timezone="";
        public String display() {return name;}
    }
    public List<Place> searchCities(String term) throws Exception {
        String url="https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(term,"UTF-8")+"&count=10&language=en&format=json";
        JSONObject j=cachedGet(url,24*3600_000L,7*24*3600_000L,false);
        List<Place> places=new ArrayList<>();JSONArray a=j.optJSONArray("results");
        if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject p=a.optJSONObject(i);if(p==null)continue;
            Place place=new Place();place.lat=p.optDouble("latitude",Double.NaN);place.lon=p.optDouble("longitude",Double.NaN);
            place.name=p.optString("name","")+", "+p.optString("admin1","")+", "+p.optString("country","");
            place.timezone=p.optString("timezone","");
            if(Double.isFinite(place.lat)&&Double.isFinite(place.lon))places.add(place);
        }
        return places;
    }
    public String fetchAqi(double lat,double lon) throws Exception {
        String url="https://air-quality-api.open-meteo.com/v1/air-quality?"+geo(lat,lon)+"&current=us_aqi,pm2_5,pm10&timezone=auto";
        JSONObject data=cachedGet(url,60*60_000L,6*3600_000L,false);
        JSONObject c=data.optJSONObject("current");if(c==null)return "AQI not available";
        int aqi=c.optInt("us_aqi",-1);
        double pm=c.optDouble("pm2_5",Double.NaN);
        return (aqi>=0?"US AQI "+aqi:"AQI unavailable") + (Double.isFinite(pm)?String.format(Locale.US," · PM2.5 %.1f μg/m³",pm):"") + " · model estimate";
    }
}
