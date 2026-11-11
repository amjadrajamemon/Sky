package com.skycast.pro;

import android.content.Context;
import android.content.SharedPreferences;

/** Local, private configuration. No account or tracking service. */
public final class AppPrefs {
    private final SharedPreferences prefs;
    public AppPrefs(Context c) { this(c,"skycast_next"); }
    private AppPrefs(Context c,String name) { prefs=c.getSharedPreferences(name, Context.MODE_PRIVATE); }
    static AppPrefs forWidget(Context context,int id) {
        return new AppPrefs(context,"skycast_widget_forecast_"+id);
    }
    static void deleteWidget(Context context,int id) {
        context.deleteSharedPreferences("skycast_widget_forecast_"+id);
    }
    void setWidgetQuery(double lat,double lon,String city,String zone,String model,int refreshMinutes) {
        String query=Double.toHexString(lat)+"|"+Double.toHexString(lon)+"|"+city+"|"+zone+"|"+model;
        if(query.equals(prefs.getString("widgetQuery",""))){
            if(refreshMinutes()!=refreshMinutes)setRefreshMinutes(refreshMinutes);
            return;
        }
        SharedPreferences.Editor edit=prefs.edit().clear().putString("widgetQuery",query)
                .putLong("lat",Double.doubleToLongBits(lat)).putLong("lon",Double.doubleToLongBits(lon))
                .putString("city",city).putString("model",model).putInt("refreshMinutes",refreshMinutes);
        if(zone!=null&&!zone.isEmpty())edit.putString("zone",zone);
        edit.apply();
    }
    public double lat() { return Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(Double.NaN))); }
    public double lon() { return Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(Double.NaN))); }
    public boolean hasLocation() { return Double.isFinite(lat()) && Double.isFinite(lon()); }
    public String city() { return prefs.getString("city","Choose your city"); }
    public String model() { return prefs.getString("model","smart"); }
    public boolean knownTimezone() { return prefs.contains("zone"); }
    public String timezone() { return prefs.getString("zone",java.util.TimeZone.getDefault().getID()); }
    public boolean fahrenheit() { return prefs.getBoolean("f",false); }
    public boolean compare() { return prefs.getBoolean("compare",true); }
    public int refreshMinutes() { return prefs.getInt("refreshMinutes",60); }
    public void setRefreshMinutes(int min) { prefs.edit().putInt("refreshMinutes",Math.max(15,Math.min(180,min))).apply(); }
    public int appTheme() {return Math.max(0,Math.min(WidgetSettings.THEMES.length-1,prefs.getInt("appTheme",0)));}
    public void setAppTheme(int theme) {prefs.edit().putInt("appTheme",Math.max(0,Math.min(WidgetSettings.THEMES.length-1,theme))).apply();}
    public boolean radarEnabled() { return prefs.getBoolean("radarEnabled",true); }
    public void setRadarEnabled(boolean val) { prefs.edit().putBoolean("radarEnabled",val).apply(); }
    public boolean rainAlerts() { return prefs.getBoolean("rainAlerts",false); }
    public void setRainAlerts(boolean val) { prefs.edit().putBoolean("rainAlerts",val).apply(); }
    public boolean gps() { return prefs.getBoolean("gps",false); }
    public long lastSuccess() { return prefs.getLong("lastSuccess",0); }
    public void setLocation(double lat,double lon,String city) {
        if(!Double.isFinite(lat)||lat < -90||lat>90||!Double.isFinite(lon)||lon < -180||lon>180) return;
        prefs.edit().putLong("lat",Double.doubleToLongBits(lat)).putLong("lon",Double.doubleToLongBits(lon)).putString("city",city).remove("zone").apply();
    }
    public void setModel(String v) { prefs.edit().putString("model",v).apply(); }
    public void setTimezone(String v) { if(v!=null&&!v.isEmpty()) prefs.edit().putString("zone",v).apply(); }
    public void setFahrenheit(boolean v) { prefs.edit().putBoolean("f",v).apply(); }
    public void setCompare(boolean v) { prefs.edit().putBoolean("compare",v).apply(); }
    public void setGps(boolean v) { prefs.edit().putBoolean("gps",v).apply(); }
    public void saveForecast(String snapshot, long fetchedAt) {
        prefs.edit().putString("lastForecast", snapshot).putLong("lastSuccess", fetchedAt).apply();
    }
    public String get(String key,String fallback) { return prefs.getString(key,fallback); }
    public void put(String key,String val) { prefs.edit().putString(key,val).apply(); }
    public long getLong(String key) { return prefs.getLong(key,0); }
    public void putLong(String key,long val) { prefs.edit().putLong(key,val).apply(); }
}
