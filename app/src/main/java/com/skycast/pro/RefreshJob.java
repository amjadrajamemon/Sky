package com.skycast.pro;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RefreshJob extends JobService {
    private static final int PERIODIC = 0x5C571;
    private static final int WIDGET_JOB_BASE = 0x20000000;
    private final Map<JobParameters, Batch> batches = new HashMap<>();

    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        AppPrefs app = new AppPrefs(context);
        int minutes = app.refreshMinutes();
        for (int id : WeatherWidget.activeWidgets(context).keySet()) {
            minutes = Math.min(minutes, WidgetSettings.load(context, id).refreshMinutes(app));
        }
        long interval = minutes * 60_000L;
        JobInfo existing = scheduler.getPendingJob(PERIODIC);
        if (existing != null && existing.getIntervalMillis() == interval) return;
        scheduler.schedule(new JobInfo.Builder(PERIODIC, new ComponentName(context, RefreshJob.class))
                .setPeriodic(interval, 5 * 60_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build());
    }

    static void requestWidget(Context context, int id, boolean force) {
        WidgetSettings settings = WidgetSettings.load(context, id);
        AppPrefs scope = settings.forecastPrefs(context, id);
        int minutes = settings.refreshMinutes(new AppPrefs(context));
        if (!scope.hasLocation() || (!force && !due(scope, minutes))) return;
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        int target = settings.independent() ? id : 0;
        int jobId = WIDGET_JOB_BASE + target;
        if (!force && scheduler.getPendingJob(jobId) != null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putInt("widget", target);
        extras.putBoolean("force", force);
        scheduler.schedule(new JobInfo.Builder(jobId, new ComponentName(context, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras).setMinimumLatency(0).build());
    }

    static void cancelWidget(Context context, int id) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(WIDGET_JOB_BASE + id);
    }

    private static boolean due(AppPrefs scope, int minutes) {
        long now = System.currentTimeMillis();
        long age = now - scope.lastSuccess();
        return age < 0 || age >= minutes * 60_000L;
    }

    @Override public boolean onStartJob(JobParameters params) {
        AppPrefs app = new AppPrefs(this);
        int target = params.getExtras().getInt("widget", -1);
        boolean force = params.getExtras().getBoolean("force", false);
        List<Integer> independent = new ArrayList<>();
        boolean refreshApp = target == 0 && app.hasLocation();
        if (target > 0 && WeatherWidget.kindFor(this, target) >= 0) {
            WidgetSettings settings = WidgetSettings.load(this, target);
            if (settings.independent() && settings.forecastPrefs(this, target).hasLocation()) {
                independent.add(target);
            } else if (!settings.independent()) {
                refreshApp = app.hasLocation();
            }
        }
        if (target < 0) {
            int interval = app.refreshMinutes();
            for (int id : WeatherWidget.activeWidgets(this).keySet()) {
                WidgetSettings settings = WidgetSettings.load(this, id);
                int minutes = settings.refreshMinutes(app);
                if (settings.independent()) {
                    AppPrefs scope = settings.forecastPrefs(this, id);
                    if (scope.hasLocation() && due(scope, minutes)) independent.add(id);
                } else {
                    interval = Math.min(interval, minutes);
                }
            }
            refreshApp = app.hasLocation() && due(app, interval);
        }
        int count = independent.size() + (refreshApp ? 1 : 0);
        if (count == 0) return false;
        batches.put(params, new Batch(count, target >= 0));
        if (refreshApp) WeatherCoordinator.refresh(this, force, (forecast, error) -> finishOne(params, forecast));
        for (int id : independent) {
            AppPrefs scope = WidgetSettings.load(this, id).forecastPrefs(this, id);
            WeatherCoordinator.refreshWidget(this, scope, force, (forecast, error) -> {
                WeatherWidget.updateOne(this, id);
                finishOne(params, forecast);
            });
        }
        return true;
    }

    private void finishOne(JobParameters params, Forecast forecast) {
        Batch batch = batches.get(params);
        if (batch == null) return;
        batch.failed |= forecast == null;
        if (--batch.remaining == 0) {
            batches.remove(params);
            jobFinished(params, batch.retry && batch.failed);
        }
    }

    @Override public boolean onStopJob(JobParameters params) {
        batches.remove(params);
        return true;
    }

    private static final class Batch {
        int remaining;
        boolean failed;
        final boolean retry;
        Batch(int remaining, boolean retry) { this.remaining = remaining; this.retry = retry; }
    }
}
