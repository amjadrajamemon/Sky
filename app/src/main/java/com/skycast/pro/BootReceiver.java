package com.skycast.pro;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) {
            WeatherWidget.updateAll(c);
            RefreshJob.schedule(c);
            for (int id : WeatherWidget.activeWidgets(c).keySet()) RefreshJob.requestWidget(c, id, false);
        }
    }
}
