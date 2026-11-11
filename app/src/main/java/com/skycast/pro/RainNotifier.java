package com.skycast.pro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Opt-in forecast alert, never presented as an official severe-weather warning. */
public final class RainNotifier {
    private static final String CHANNEL="skycast_rain_forecast";
    private RainNotifier(){}
    public static void evaluate(Context context,Forecast forecast) {
        if(forecast==null||forecast.stale)return;
        AppPrefs prefs=new AppPrefs(context);if(!prefs.rainAlerts())return;
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        RainNowcast n=forecast.nowcast;
        long now=System.currentTimeMillis();
        boolean radarWarning=n!=null && n.current(now) && n.validRadar(now) && n.radarMoving
                && n.arrivalStart>=0 && n.arrivalStart<=45 && (n.bins[0]>=2||n.bins[1]>=2||n.bins[2]>=2||n.bins[3]>=2||n.bins[4]>=2);
        ForecastMath.RainWindow rain=ForecastMath.rainWindow(forecast,java.time.Instant.now());
        if(!radarWarning && rain.nextSixHourChance<70)return;
        if(now-prefs.getLong("lastRainNotified")<(radarWarning?90*60_000L:6*3600_000L))return;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Rain forecast",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(context,1,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String outlook=radarWarning ? n.headline+" · "+n.intensity.toLowerCase(java.util.Locale.US)
                :rain.nextSixHourChance+"% peak hourly model rain chance in the next 6 h";
        Notification note=new Notification.Builder(context,CHANNEL)
            .setContentTitle(radarWarning?"Radar echoes approaching · estimate":"Possible rain near "+forecast.city)
            .setContentText(outlook)
            .setSmallIcon(R.drawable.ic_launcher).setContentIntent(open).setAutoCancel(true)
            .setStyle(new Notification.BigTextStyle().bigText(outlook+"\n"+(radarWarning?"RainViewer past radar; extrapolated, not observed rain":"Source · "+forecast.source)))
            .build();
        manager.notify(2020,note);prefs.putLong("lastRainNotified",now);
    }
}
