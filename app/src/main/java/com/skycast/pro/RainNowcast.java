package com.skycast.pro;


/** Best-effort two-hour rain signal. Unknown never means dry. */
public final class RainNowcast {
    public long updatedAt;
    public long radarTime;
    public long observationTime;
    public String radarState="unavailable", observation="No recent nearby observation";
    public String headline="Rain nowcast unavailable", detail="Waiting for data";
    public String intensity="Unknown", evidence="Limited evidence", verification="Not yet verified";
    public String station="";
    public int arrivalStart=-1, arrivalEnd=-1, duration=-1;
    public int modelChance=-1;
    public double modelMm=Double.NaN;
    public boolean radarCoverage=false, radarAvailable=false, radarMoving=false, observedRain=false;
    /** 10-minute radar extrapolation, values 0 (none), 1-4 (relative echoes), -1 (unknown). */
    public int[] bins = new int[13];
    public RainNowcast(){java.util.Arrays.fill(bins,-1);}
    public boolean current(long now){return updatedAt>0 && now>=updatedAt && now-updatedAt<=25*60_000L;}
    public boolean validRadar(long now){return radarAvailable&&radarCoverage&&radarTime>0&&Math.abs(now-radarTime)<35*60_000L;}
}
