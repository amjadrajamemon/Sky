package com.skycast.pro;

/** Deterministic retry policy; availability and speed never imply forecast accuracy. */
public final class SourcePolicy {
    private SourcePolicy() {}

    public static String endpoint(String model) {
        switch (model) {
            case "best_match":
            case "ecmwf_ifs":
            case "ncep_gfs_seamless":
            case "icon_seamless":
                return "open-meteo-forecast";
            default:
                return model;
        }
    }

    public static boolean endpointUnavailable(Exception failure) {
        if (failure instanceof java.net.SocketTimeoutException
                || failure instanceof java.net.ConnectException
                || failure instanceof java.net.UnknownHostException) {
            return true;
        }
        String text = failure.getMessage();
        return text != null && (text.contains("HTTP 429") || text.contains("HTTP 403")
                || text.matches("(?s).*HTTP 5[0-9]{2}.*"));
    }

    public static long cooldownMillis(int consecutiveFailures,String error) {
        if(error!=null&&error.contains("429"))return 30*60_000L;
        if(error!=null&&error.contains("403"))return 60*60_000L;
        int power=Math.min(5,Math.max(0,consecutiveFailures-1));
        return Math.min(60*60_000L,120_000L*(1L<<power));
    }
    public static long smoothedLatency(long previous,long measured) {
        measured=Math.max(1,measured);return previous<=0?measured:(previous*3+measured)/4;
    }
    public static boolean coolingDown(long until,long now) {
        // A moved system clock must not suppress a source indefinitely.
        return until>now&&until-now<=60*60_000L;
    }
}
