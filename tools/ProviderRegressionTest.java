import com.skycast.pro.*;
import org.json.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;

/** Fixtures exercise provider failures and date/unit boundaries, without live network dependencies. */
public final class ProviderRegressionTest {
    static int passed;
    static final double LAT=33.6844,LON=73.0479;
    static final Instant NOW=Instant.parse("2026-11-10T19:30:00Z");
    interface Check {void run()throws Exception;}
    static void test(String name,Check check)throws Exception{
        try{check.run();passed++;}catch(Throwable e){throw new AssertionError(name,e);}
    }
    static void yes(boolean value){if(!value)throw new AssertionError();}
    static void near(double x,double y){yes(Math.abs(x-y)<0.02);}
    static void rejects(Check check)throws Exception{
        try{check.run();}catch(java.io.IOException e){return;}throw new AssertionError("Expected provider rejection");
    }
    static String msn(String unit){
        return "<weatherdata><weather lat='45' long='100' degreetype='C'><current temperature='99'/></weather>"
            +"<weather lat='33.684' long='73.048' provider='Foreca' degreetype='"+unit+"'>"
            +"<current temperature='"+(unit.equals("F")?"68":"18")+"' feelslike='18' humidity='66' windspeed='6 km/h' skytext='Mostly cloudy' date='2026-11-10' observationtime='23:40:00'/>"
            +"<forecast low='14' high='25' date='2026-11-11' skytextday='Mostly sunny' precip='2'/>"
            +"<forecast low='14' high='19' date='2026-11-12' skytextday='Rain showers' precip='84'/></weather></weatherdata>";
    }
    static Forecast msnParse(String x,Instant now)throws Exception{
        return PublicWeatherParser.msn(x,LAT,LON,"Islamabad","Asia/Karachi",now);
    }
    static String accu(LocalDate day,String unit){
        StringBuilder b=new StringBuilder("<html><script type='application/ld+json'>{\"@type\":\"Place\",\"geo\":{\"latitude\":33.71,\"longitude\":73.076}}</script>");
        b.append("<a class='cur-con-weather-card'><p class='cur-con-weather-card__subtitle'>12:10 AM</p><div class='temp'>")
            .append(unit.equals("F")?"68":"18").append("°<span class='after-temp'>").append(unit)
            .append("</span></div><div class='real-feel'>RealFeel® 17°</div><span class='phrase'>Thunderstorm</span>")
            .append("<div class='detail'><span class='label'>Wind</span><span class='value'>S 6 km/h</span></div></a>");
        for(int i=0;i<7;i++){
            LocalDate d=day.plusDays(i);
            b.append("<a class='daily-list-item'><div class='date'><p>Today</p><p>")
                .append(d.getMonthValue()).append('/').append(d.getDayOfMonth()).append("</p></div>")
                .append("<span class='temp-hi'>25°</span><span class='temp-lo'>16°</span><div class='phrase'><p>Hazy</p></div><div class='precip'>2%</div></a>");
        }
        return b.append("</html>").toString();
    }
    static Forecast accuParse(String x,Instant now)throws Exception{
        return PublicWeatherParser.accuWeather(x,LAT,LON,"Islamabad","Asia/Karachi",now);
    }
    static JSONObject google()throws Exception{
        JSONObject h=new JSONObject(),d=new JSONObject();JSONArray times=new JSONArray(),temp=new JSONArray(),rain=new JSONArray(),code=new JSONArray(),wind=new JSONArray(),day=new JSONArray();
        LocalDateTime t=LocalDateTime.parse("2026-11-11T00:00");
        for(int i=0;i<168;i++){times.put(t.plusHours(i).toString());temp.put(10+i%24);rain.put(0.2);code.put(51);wind.put(5);day.put(i%24>=6&&i%24<18?1:0);}
        h.put("time",times).put("temperature_2m",temp).put("precipitation",rain).put("weather_code",code).put("wind_speed_10m",wind).put("is_day",day);
        JSONArray dates=new JSONArray(),high=new JSONArray(),low=new JSONArray(),mm=new JSONArray();
        for(int i=0;i<7;i++){dates.put(t.toLocalDate().plusDays(i).toString());high.put(33);low.put(10);mm.put(4.8);}
        d.put("time",dates).put("temperature_2m_max",high).put("temperature_2m_min",low).put("precipitation_sum",mm);
        return new JSONObject().put("timezone","Asia/Karachi").put("hourly",h).put("daily",d);
    }
    static Forecast googleParse(JSONObject j)throws Exception{return GoogleWeatherParser.parse(j,LAT,LON,"Islamabad","Asia/Karachi",NOW);}
    static JSONObject openMeteo() throws Exception {
        return new JSONObject("""
            {
              "latitude":33.68,"longitude":73.05,"timezone":"Asia/Karachi",
              "current":{"time":"2026-11-11T00:15","temperature_2m":17},
              "hourly":{"time":["2026-11-11T00:00","2026-11-11T01:00","2026-11-11T02:00"],
                        "precipitation":[8,0.2,0],"precipitation_probability":[95,20,0]},
              "daily":{"time":["2026-11-11","2026-11-12","2026-11-13"],
                       "temperature_2m_max":[24,12,21],"temperature_2m_min":[15,21,13],
                       "precipitation_probability_max":[45,10,144],"precipitation_sum":[2,1,-2]}
            }
            """);
    }
    static Forecast parseOpenMeteo(JSONObject j, Instant now) throws Exception {
        Forecast f = OpenMeteoForecastParser.parse(j,LAT,LON,"Islamabad","Asia/Karachi","ECMWF IFS",now);
        ForecastQuality.prepare(f,now);
        return f;
    }
    public static void main(String[] args)throws Exception{
        test("MSN chooses coordinates rather than first city match",()->near(msnParse(msn("C"),NOW).temperatureC,18));
        test("MSN daily probability retained without hourly invention",()->{Forecast f=msnParse(msn("C"),NOW);yes(f.days.get(1).rainProbability==84&&f.hours.isEmpty()&&Double.isNaN(f.days.get(0).rainMm));});
        test("MSN conversion Fahrenheit to Celsius",()->near(msnParse(msn("F"),NOW).temperatureC,20));
        test("MSN rejects six-hour-old observations",()->rejects(()->msnParse(msn("C"),NOW.plusSeconds(7*3600))));
        test("MSN rejects future observations",()->rejects(()->msnParse(msn("C"),NOW.minusSeconds(3*3600))));
        test("MSN rejects wrong-city coordinates",()->rejects(()->msnParse(msn("C").replace("33.684'","40.684'"),NOW)));
        test("MSN rejects missing observation time",()->rejects(()->msnParse(msn("C").replace("23:40:00",""),NOW)));
        test("MSN rejects malformed or blocked payload",()->rejects(()->msnParse("<html>Access denied</html>",NOW)));
        test("MSN missing probability remains unavailable",()->yes(msnParse(msn("C").replace("precip='2'","precip=''"),NOW).days.get(0).rainProbability==-1));
        test("MSN keeps provider's condition text",()->yes(msnParse(msn("C"),NOW).conditionText.equals("Mostly cloudy")));
        String html=accu(LocalDate.parse("2026-11-11"),"C");
        test("Accu public markup produces seven native widget days",()->{Forecast f=accuParse(html,NOW);yes(f.days.size()==7&&f.source.startsWith("AccuWeather")&&f.hours.isEmpty());near(f.temperatureC,18);yes(f.days.get(0).rainProbability==2);});
        test("Accu accepts HTML entity in JSON-LD type",()->yes(accuParse(html.replace("ld+json","ld&#x2B;json"),NOW).days.size()==7));
        test("Accu rejects wrong-city metadata",()->rejects(()->accuParse(html.replace("33.71","42.71"),NOW)));
        test("Accu rejects missing coordinates",()->rejects(()->accuParse(html.replace("\"geo\"","\"missingGeo\""),NOW)));
        test("Accu rejects changed current-weather selectors",()->rejects(()->accuParse(html.replace("cur-con-weather-card'","changed-card'"),NOW)));
        test("Accu rejects stale observation clocks",()->rejects(()->accuParse(html,NOW.plusSeconds(8*3600))));
        test("Accu rejects yesterday's page",()->rejects(()->accuParse(html,NOW.plusSeconds(24*3600))));
        test("Accu converts units instead of assuming Celsius",()->near(accuParse(accu(LocalDate.parse("2026-11-11"),"F"),NOW).temperatureC,20));
        test("Accu missing probability is not zero",()->yes(accuParse(html.replace("2%","—"),NOW).days.get(0).rainProbability==-1));
        test("Accu handles year boundary",()->yes(accuParse(accu(LocalDate.parse("2026-12-31"),"C"),Instant.parse("2026-12-30T19:30:00Z")).days.get(1).date.equals("2027-01-01")));
        test("Google selects current hour in location timezone",()->near(googleParse(google()).temperatureC,10));
        test("Google supplies 48 rain hours and seven days",()->{Forecast f=googleParse(google());yes(f.hours.size()==48&&f.days.size()==7);});
        test("Google does not fabricate probabilities or unsupported fields",()->{Forecast f=googleParse(google());yes(f.days.get(0).rainProbability==-1&&f.hours.get(0).rainProbability==-1&&Double.isNaN(f.humidityPct)&&Double.isNaN(f.feelsLikeC));});
        test("Google refuses empty current data",()->{JSONObject j=google();j.getJSONObject("hourly").put("temperature_2m",new JSONArray());rejects(()->googleParse(j));});
        test("Google rejects missing daily data",()->{JSONObject j=google();j.remove("daily");rejects(()->googleParse(j));});
        test("Google rejects invalid timezone",()->rejects(()->googleParse(google().put("timezone","Invalid/Nowhere"))));
        test("Google rejects individual member mislabeled as mean",()->{JSONObject j=google();j.getJSONObject("hourly").put("temperature_2m_member01",new JSONArray());rejects(()->googleParse(j));});
        test("Google preserves original fetch time on cached data",()->{Forecast f=googleParse(google().put("_skycast_fetched_at",NOW.minusSeconds(1800).toEpochMilli()).put("_skycast_cache",true));yes(f.cached&&f.fetchedAt==NOW.minusSeconds(1800).toEpochMilli());});
        test("Google provider errors fail into fallback",()->rejects(()->googleParse(new JSONObject().put("error",true).put("reason","rate limit"))));
        test("Backoff increases and remains bounded",()->yes(SourcePolicy.cooldownMillis(2,"")>SourcePolicy.cooldownMillis(1,"")&&SourcePolicy.cooldownMillis(100,"")<=3600_000));
        test("HTTP 429 receives longer pause",()->yes(SourcePolicy.cooldownMillis(1,"HTTP 429")==1800_000));
        test("Backoff tolerates moved system clock",()->yes(!SourcePolicy.coolingDown(100_000_000,0)));
        test("Latency smoothing avoids sudden ranking jumps",()->yes(SourcePolicy.smoothedLatency(1000,5000)==2000));
        test("All seven days fit the normal four-column width",()->yes(WidgetSizing.weeklyDayCount(280,7,1)==7));
        test("Weekly forecast adapts to narrower width and larger text",()->{
            yes(WidgetSizing.weeklyDayCount(230,7,1)<7);
            yes(WidgetSizing.weeklyDayCount(280,7,1.3f)<7);
        });
        test("Provider stays visible in compact footer",()->yes(WidgetSizing.source("MSN public feed · Foreca").equals("MSN · Foreca")));
        test("Open-Meteo rain is assigned to the preceding interval",()->{
            Forecast f=parseOpenMeteo(openMeteo(),NOW);
            yes(f.hours.size()==2 && f.hours.get(0).time.equals("2026-11-11T00:00"));
            near(f.hours.get(0).rainMm,0.2);
            yes(f.hours.get(0).rainProbability==20);
        });
        test("Provider quality discards inverted highs and invalid optional rain",()->{
            Forecast f=parseOpenMeteo(openMeteo(),NOW);
            yes(f.days.size()==2 && f.days.get(1).date.equals("2026-11-13"));
            yes(Double.isNaN(f.days.get(1).rainMm) && f.days.get(1).rainProbability==-1);
        });
        test("A fresh HTTP fetch cannot make an old forecast current",()->{
            JSONObject j=openMeteo();
            j.getJSONObject("current").put("time","2026-11-10T19:15");
            yes(parseOpenMeteo(j,NOW).stale);
        });
        test("Forecast grid mismatch is rejected",()->rejects(()->parseOpenMeteo(openMeteo().put("latitude",45),NOW)));
        test("A forecast without usable days is rejected",()->{
            JSONObject j=openMeteo();
            j.remove("daily");
            rejects(()->parseOpenMeteo(j,NOW));
        });
        test("Regional verification applies near Islamabad after the benchmark",()->{
            Instant date=Instant.parse("2026-11-11T08:00:00Z");
            yes(LocalForecastSkill.applies(LAT,LON,date));
            yes(!LocalForecastSkill.applies(31.55,74.34,date));
            yes(!LocalForecastSkill.applies(LAT,LON,date.plusSeconds(100L*86400)));
        });
        test("Transport failures skip the shared forecast endpoint",()->{
            yes(SourcePolicy.endpoint("ecmwf_ifs").equals(SourcePolicy.endpoint("best_match")));
            yes(!SourcePolicy.endpoint("ecmwf_ifs").equals(SourcePolicy.endpoint("metno")));
            yes(SourcePolicy.endpointUnavailable(new java.net.SocketTimeoutException()));
            yes(SourcePolicy.endpointUnavailable(new java.io.IOException("Weather provider HTTP 503")));
            yes(!SourcePolicy.endpointUnavailable(new java.io.IOException("Invalid current temperature")));
        });
        if(args.length>0){
            Path p=Path.of(args[0]);Instant live=Instant.parse("2026-11-10T20:00:00Z");
            test("Live Google response schema",()->{JSONObject j=new JSONObject(Files.readString(p.resolve("google-weathernext-mean-live.json")));Forecast f=GoogleWeatherParser.parse(j,LAT,LON,"Islamabad","Asia/Karachi",live);yes(f.days.size()==7&&f.hours.size()==48);});
            test("Live MSN coordinate response",()->yes(msnParse(Files.readString(p.resolve("msn-coordinate-weather.xml")),live).source.contains("MSN")));
            test("Live AccuWeather redirected location response",()->yes(accuParse(Files.readString(p.resolve("accuweather-lookup.html")),live).days.size()==7));
        }
        System.out.println(passed+" provider, freshness, unit, fallback-policy and live-schema checks passed");
    }
}
