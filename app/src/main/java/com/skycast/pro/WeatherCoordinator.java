package com.skycast.pro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.time.Instant;

/** Serial worker prevents overlapping refresh requests and widget refresh races. */
public final class WeatherCoordinator {
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    public interface Callback {void done(Forecast forecast, String message);}
    private WeatherCoordinator() {}
    public static void refresh(Context context,boolean force,Callback cb) {
        refresh(context,new AppPrefs(context),force,true,cb);
    }
    static void refreshWidget(Context context,AppPrefs prefs,boolean force,Callback cb) {
        refresh(context,prefs,force,false,cb);
    }
    private static void refresh(Context context,AppPrefs p,boolean force,boolean appForecast,Callback cb) {
        final Context app=context.getApplicationContext();
        EXEC.execute(()->{
            Forecast forecast=null;String error="";
            WeatherApi api=new WeatherApi(p);ForecastStore store=new ForecastStore(p);
            double lat=p.lat(),lon=p.lon();
            if(!p.hasLocation()) {
                if(cb!=null)new Handler(Looper.getMainLooper()).post(()->cb.done(null,"Location not selected"));
                return;
            }
            String model=p.model();
            boolean smart="smart".equals(model);
            boolean localVerification=smart && LocalForecastSkill.applies(lat,lon,Instant.now());
            String locationKey=Math.round(lat*20)+"_"+Math.round(lon*20);
            String actualModel="";
            Forecast staleCandidate=null;
            boolean online=isOnline(app);
            List<String> sources=orderedSources(p,model,locationKey,localVerification);
            Set<String> unavailableEndpoints=new LinkedHashSet<>();
            long started=SystemClock.elapsedRealtime();int attempts=0;
            if(online)for(String candidate:sources){
                long now=System.currentTimeMillis();String health="source_"+candidate+"_"+locationKey;
                String endpoint=SourcePolicy.endpoint(candidate);
                boolean primary=attempts==0;
                if(unavailableEndpoints.contains(endpoint))continue;
                if(!(force&&primary)&&SourcePolicy.coolingDown(p.getLong(health+"_until"),now))continue;
                if(attempts>=3||(attempts>0&&SystemClock.elapsedRealtime()-started>24_000L))break;
                attempts++;long requestStart=SystemClock.elapsedRealtime();
                try{
                    Forecast result=api.fetchModel(lat,lon,candidate,force&&primary);
                    ForecastQuality.prepare(result,Instant.now());
                    if(result.stale){
                        staleCandidate=newest(staleCandidate,result);
                        throw new java.io.IOException("Source returned stale data. "+result.status);
                    }
                    forecast=result;actualModel=candidate;
                    p.putLong(health+"_failures",0);p.putLong(health+"_until",0);
                    p.putLong(health+"_latency",SourcePolicy.smoothedLatency(p.getLong(health+"_latency"),SystemClock.elapsedRealtime()-requestStart));
                    if(smart)p.put("lastGoodSource_"+locationKey,candidate);
                    break;
                }catch(Exception e){
                    long failures=Math.min(10,p.getLong(health+"_failures")+1);p.putLong(health+"_failures",failures);
                    p.putLong(health+"_until",now+SourcePolicy.cooldownMillis((int)failures,e.getMessage()));
                    if(SourcePolicy.endpointUnavailable(e))unavailableEndpoints.add(endpoint);
                    error="Could not refresh "+WeatherApi.modelLabel(candidate)+": "+e.getMessage();
                }
            }
            else error="Offline · using saved weather data";
            if(forecast==null){
                forecast=newest(staleCandidate,store.load());
                if(error.isEmpty())error="Waiting for a weather provider to respond";
                if(forecast!=null){
                    forecast.stale=true;
                    forecast.cached=true;
                    forecast.status=error;
                }
            } else {
                forecast.city=p.city();
                if(appForecast && smart && p.compare() && SystemClock.elapsedRealtime()-started<8000L) {
                    try {
                        Forecast independent="metno".equals(actualModel)?api.fetchOpenMeteo(lat,lon,"best_match",false):api.fetchMetNorway(lat,lon,false);
                        ForecastQuality.prepare(independent,Instant.now());
                        forecast.status+=(forecast.status.isEmpty()?"":"\n")+ForecastMath.temperatureAgreement(forecast.temperatureC,independent.temperatureC)
                         + " · "+forecast.source+" vs "+independent.source;
                        if(independent.stale)forecast.status+=" · comparison cached";
                    }catch(Exception ignored){forecast.status+="\nIndependent comparison temporarily unavailable";}
                }
                String routing=smart?"Smart Auto · "+WeatherApi.modelLabel(actualModel)
                    :actualModel.equals(model)?"Selected source active":"Backup source · "+WeatherApi.modelLabel(actualModel);
                forecast.status+=(forecast.status.isEmpty()?"":"\n")+routing;
                if(localVerification && LocalForecastSkill.MODEL.equals(actualModel)){
                    forecast.status+="\n"+LocalForecastSkill.evidence();
                }
                error="";
            }
            if(Double.compare(p.lat(),lat)!=0 || Double.compare(p.lon(),lon)!=0 || !p.model().equals(model)){
                forecast=store.load();
                error="Location or source changed";
            } else if(forecast!=null){
                p.setTimezone(forecast.timezone);
                store.save(forecast);
                if(appForecast)RainNotifier.evaluate(app,forecast);
            }
            final Forecast ready=forecast;final String msg=error;
            if(appForecast || cb!=null)new Handler(Looper.getMainLooper()).post(()->{
                if(appForecast)WeatherWidget.updateAll(app);
                if(cb!=null)cb.done(ready,msg);
            });
        });
    }
    private static Forecast newest(Forecast first,Forecast second){
        if(first==null)return second;
        if(second==null)return first;
        return first.fetchedAt>=second.fetchedAt?first:second;
    }
    private static List<String> orderedSources(AppPrefs p,String selected,String locationKey,boolean localVerification){
        Set<String> order=new LinkedHashSet<>();
        if(!"smart".equals(selected))order.add(selected);
        else if(localVerification)order.add(LocalForecastSkill.MODEL);
        order.add("best_match");
        if("smart".equals(selected)){
            String good=p.get("lastGoodSource_"+locationKey,"");
            for(String id:WeatherApi.COMPARE_IDS)if(id.equals(good)){order.add(good);break;}
        }
        order.add("metno");
        order.add(WeatherApi.GOOGLE_MODEL);order.add("msn_public");order.add("accu_public");
        List<String> backups=new ArrayList<>();
        for(String id:WeatherApi.COMPARE_IDS)if(!order.contains(id))backups.add(id);
        backups.sort((a,b)->Long.compare(score(p,a),score(p,b)));
        order.addAll(backups);return new ArrayList<>(order);
    }
    private static long score(AppPrefs p,String model){
        long time=p.getLong("source_"+model+"_latency");return time>0?time:2000L;
    }
    public static boolean isOnline(Context c){
        try{
            ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(m==null)return true;
            NetworkCapabilities n=m.getNetworkCapabilities(m.getActiveNetwork());
            return n!=null&&n.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }catch(Exception e){return true;}
    }
}
