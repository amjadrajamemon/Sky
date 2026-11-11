package com.skycast.pro;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.json.JSONObject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public interfaces only. Reject absent, ambiguous, distant, stale, or changed content. */
public final class PublicWeatherParser {
    private PublicWeatherParser() {}
    private static final Pattern NUMBER=Pattern.compile("[-−]?\\d+(?:\\.\\d+)?");

    public static Forecast msn(String xml,double lat,double lon,String city,String timezone,Instant now) throws IOException {
        Document doc=Jsoup.parse(xml,"",Parser.xmlParser());
        Element nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(Element w:doc.select("weather")){
            double d=distanceKm(lat,lon,numeric(w.attr("lat")),numeric(w.attr("long")));
            if(d<distance){nearest=w;distance=d;}
        }
        if(nearest==null||distance>75)throw new IOException("MSN location match unavailable");
        Element current=nearest.selectFirst("current");
        if(current==null)throw new IOException("MSN current data unavailable");
        String unit=nearest.attr("degreetype");
        if(!unit.equals("C")&&!unit.equals("F"))throw new IOException("MSN temperature units unavailable");
        ZoneId zone=zone(timezone);
        LocalDateTime observed;
        try{observed=LocalDateTime.parse(current.attr("date")+"T"+current.attr("observationtime"));}
        catch(Exception e){throw new IOException("MSN observation timestamp missing",e);}
        requireFresh(observed,zone,now);
        Forecast f=base("MSN public feed · "+nearest.attr("provider"),lat,lon,city,zone,now);
        f.temperatureC=celsius(numeric(current.attr("temperature")),unit.equals("F"));
        f.feelsLikeC=celsius(numeric(current.attr("feelslike")),unit.equals("F"));
        f.humidityPct=numeric(current.attr("humidity"));f.windKmh=wind(current.attr("windspeed"));
        f.conditionText=current.attr("skytext");f.weatherCode=conditionCode(f.conditionText);
        int hour=LocalDateTime.ofInstant(now,zone).getHour();f.day=hour>=6&&hour<18;
        LocalDate today=LocalDateTime.ofInstant(now,zone).toLocalDate();
        for(Element e:nearest.select("forecast")){
            LocalDate date;
            try{date=LocalDate.parse(e.attr("date"));}catch(Exception ex){continue;}
            if(date.isBefore(today)||date.isAfter(today.plusDays(7)))continue;
            Forecast.Day d=new Forecast.Day();d.date=date.toString();
            d.highC=celsius(numeric(e.attr("high")),unit.equals("F"));d.lowC=celsius(numeric(e.attr("low")),unit.equals("F"));
            if(!Double.isFinite(d.highC)||!Double.isFinite(d.lowC))continue;
            d.weatherCode=conditionCode(e.attr("skytextday"));d.rainProbability=chance(e.attr("precip"));f.days.add(d);
        }
        f.status="Public MSN/Foreca feed; observation "+observed+" ("+zone+"). "
            +"Public interfaces may change; no hourly rainfall feed is supplied. Daily rain chances are from the provider.";
        validate(f);return f;
    }

    public static Forecast accuWeather(String html,double lat,double lon,String city,String fallbackTimezone,Instant now) throws IOException {
        Document doc=Jsoup.parse(html);
        Element current=doc.selectFirst(".cur-con-weather-card");
        if(current==null)throw new IOException("AccuWeather public page unavailable or changed");
        JSONObject place=null;
        for(Element script:doc.select("script[type=application/ld+json]")){
            try{JSONObject j=new JSONObject(script.data());if(j.has("geo")){place=j;break;}}catch(Exception ignored){}
        }
        JSONObject geo=place==null?null:place.optJSONObject("geo");
        if(geo==null||distanceKm(lat,lon,geo.optDouble("latitude",Double.NaN),geo.optDouble("longitude",Double.NaN))>75)
            throw new IOException("AccuWeather location does not match the selected coordinates");
        String unit=text(current,".after-temp").toUpperCase(Locale.ROOT);
        if(!unit.equals("C")&&!unit.equals("F"))throw new IOException("AccuWeather temperature units unavailable");
        ZoneId zone=zone(fallbackTimezone);
        LocalDateTime localNow=LocalDateTime.ofInstant(now,zone);
        LocalDate today=localNow.toLocalDate();
        Element first=doc.selectFirst(".daily-list-item .date");
        if(first==null)throw new IOException("AccuWeather forecast dates unavailable");
        LocalDate pageDate=monthDay(text(first,"p:last-child"),today);
        if(!pageDate.equals(today))throw new IOException("AccuWeather page date is not current for this location");
        LocalTime clock;
        try{clock=LocalTime.parse(text(current,".cur-con-weather-card__subtitle").toUpperCase(Locale.US),DateTimeFormatter.ofPattern("h:mm a",Locale.US));}
        catch(Exception e){throw new IOException("AccuWeather observation time unavailable",e);}
        LocalDateTime observed=today.atTime(clock);
        if(observed.isAfter(localNow.plusMinutes(15)))observed=observed.minusDays(1);
        requireFresh(observed,zone,now);
        Forecast f=base("AccuWeather · public forecast page",lat,lon,city,zone,now);
        f.temperatureC=celsius(numeric(text(current,".temp")),unit.equals("F"));
        f.feelsLikeC=celsius(numeric(text(current,".real-feel")),unit.equals("F"));
        f.conditionText=text(current,".phrase");f.weatherCode=conditionCode(f.conditionText);
        f.day=localNow.getHour()>=6&&localNow.getHour()<18;
        for(Element e:current.select(".detail")){
            if(text(e,".label").equalsIgnoreCase("Wind"))f.windKmh=wind(text(e,".value"));
            if(text(e,".label").equalsIgnoreCase("Humidity"))f.humidityPct=numeric(text(e,".value"));
        }
        for(Element e:doc.select(".daily-list-item")){
            if(f.days.size()>=7)break;
            LocalDate date;
            try{date=monthDay(text(e,".date p:last-child"),today);}catch(IOException ex){continue;}
            if(date.isBefore(today)||date.isAfter(today.plusDays(7)))continue;
            Forecast.Day d=new Forecast.Day();d.date=date.toString();
            d.highC=celsius(numeric(text(e,".temp-hi")),unit.equals("F"));d.lowC=celsius(numeric(text(e,".temp-lo")),unit.equals("F"));
            if(!Double.isFinite(d.highC)||!Double.isFinite(d.lowC))continue;
            d.weatherCode=conditionCode(text(e,".phrase > p"));
            String precip=text(e,".precip");d.rainProbability=precip.contains("%")?chance(precip):-1;
            f.days.add(d);
        }
        f.status="AccuWeather public page; observation "+observed+" ("+zone+"). "
            +"Public page changes or access limits can interrupt this source; no hourly rainfall amounts are supplied.";
        validate(f);return f;
    }

    private static Forecast base(String source,double lat,double lon,String city,ZoneId zone,Instant now){
        Forecast f=new Forecast();f.source=source;f.city=city;f.latitude=lat;f.longitude=lon;f.timezone=zone.getId();
        f.fetchedAt=now.toEpochMilli();f.retrievedAt=LocalDateTime.ofInstant(now,zone).toString();return f;
    }
    private static void validate(Forecast f)throws IOException{
        if(!Double.isFinite(f.temperatureC)||f.temperatureC < -100||f.temperatureC>65||f.days.isEmpty())
            throw new IOException("Public forecast incomplete; trying backup");
    }
    private static ZoneId zone(String s)throws IOException{
        try{return ZoneId.of(s);}catch(Exception e){throw new IOException("Forecast timezone unknown",e);}
    }
    private static void requireFresh(LocalDateTime observed,ZoneId zone,Instant now)throws IOException{
        long age=Duration.between(observed.atZone(zone).toInstant(),now).getSeconds();
        if(age < -900||age>6*3600)throw new IOException("Provider observation is stale or has an invalid timestamp");
    }
    private static LocalDate monthDay(String s,LocalDate today)throws IOException{
        try{
            String[] p=s.trim().split("/");LocalDate date=LocalDate.of(today.getYear(),Integer.parseInt(p[0]),Integer.parseInt(p[1]));
            if(date.isBefore(today.minusMonths(6)))date=date.plusYears(1);
            if(date.isAfter(today.plusMonths(6)))date=date.minusYears(1);return date;
        }catch(Exception e){throw new IOException("Invalid forecast date",e);}
    }
    private static String text(Element e,String selector){Element x=e.selectFirst(selector);return x==null?"":x.text().trim();}
    private static double numeric(String s){
        Matcher m=NUMBER.matcher(s.replace('−','-'));if(!m.find())return Double.NaN;
        try{return Double.parseDouble(m.group());}catch(Exception e){return Double.NaN;}
    }
    private static int chance(String s){double d=numeric(s);return Double.isFinite(d)&&d>=0&&d<=100?(int)Math.round(d):-1;}
    private static double celsius(double value,boolean f){return f?(value-32)*5/9:value;}
    private static double wind(String s){
        double n=numeric(s);String unit=s.toLowerCase(Locale.ROOT);
        return unit.contains("mph")||unit.contains("mi/h")?n*1.609344:unit.contains("km/h")||unit.contains("kph")?n:Double.NaN;
    }
    public static double distanceKm(double lat,double lon,double lat2,double lon2){
        if(!Double.isFinite(lat2)||!Double.isFinite(lon2)||Math.abs(lat2)>90||Math.abs(lon2)>180)return Double.POSITIVE_INFINITY;
        double a=Math.pow(Math.sin(Math.toRadians(lat2-lat)/2),2)
            +Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(Math.toRadians(lon2-lon)/2),2);
        return 6371*2*Math.asin(Math.sqrt(Math.min(1,a)));
    }
    public static int conditionCode(String text){
        String s=text.toLowerCase(Locale.ROOT);
        if(s.contains("thunder")||s.contains("t-storm"))return 95;
        if(s.contains("sleet")||s.contains("freezing rain"))return 66;
        if(s.contains("snow")||s.contains("flurr"))return 73;
        if(s.contains("shower"))return 80;
        if(s.contains("drizzle"))return 51;
        if(s.contains("rain")||s.contains("downpour"))return 61;
        if(s.contains("fog"))return 45;
        if(s.contains("partly"))return 2;
        if(s.contains("mostly sunny")||s.contains("mostly clear"))return 1;
        if(s.contains("cloud")||s.contains("overcast"))return 3;
        if(s.contains("sunny")||s.contains("sunshine")||s.contains("clear"))return 0;
        return -1;
    }
}
