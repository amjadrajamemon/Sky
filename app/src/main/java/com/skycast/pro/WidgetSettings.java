package com.skycast.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONException;
import org.json.JSONObject;

/** Settings belong to the launcher widget ID. Defaults follow the app's forecast. */
final class WidgetSettings {
    static final int DEFAULT = 0, CURRENT = 1, FORECAST = 2, BOTH = 3;
    static final int TEMP_ONLY = 4, TEMP_RAIN = 5, TODAY_WEEK = 6, CURRENT_WEEK = 7, CURRENT_TODAY = 8, TODAY_ONLY = 9;
    static final String[] LAYOUTS = {"Live + today + seven days", "Live only", "Seven-day forecast",
            "Live + today + seven days (classic)", "Temperature only", "Temperature + rain",
            "Today + seven days", "Live + seven days", "Live + today", "Today only"};
    static final String[] THEMES = {"Aurora glass", "Arctic light", "Obsidian", "Ocean depth",
            "Forest mist", "Violet dusk", "Solar sand", "Graphite", "Neon radar", "Editorial minimal"};
    static final String[] ICON_STYLES = {"Follow theme", "Illustrated", "Outline", "Neon glow", "Soft glass", "Flat", "Monochrome", "Badge", "Pastel", "High contrast", "Minimal"};
    private static final int[] THEME_ICONS = {4, 8, 9, 4, 5, 3, 7, 6, 3, 10};
    private static final int[] THEME_FONTS = {0, 1, 2, 0, 1, 4, 3, 2, 4, 3};
    private static final int[] THEME_DENSITY = {0, 1, 0, 1, 1, 0, 1, 0, 0, 1};
    static final String[] FONTS = {"Modern sans", "Rounded sans", "Condensed", "Serif", "Monospace"};
    private static final int[] BACKGROUNDS = {0xff102b43, 0xffe8f4fb, 0xff10151e, 0xff09293b,
            0xff12372c, 0xff2b234d, 0xfff4e6c9, 0xff252b34, 0xff090d24, 0xfffdfcf8};
    private static final int[] TEXT = {0xfff2faff, 0xff143047, 0xffeff7ff, 0xffeffaff,
            0xfff2fff5, 0xfff7efff, 0xff2c241d, 0xfff0f3f8, 0xfff5f7ff, 0xff282a30};
    private static final int[] ACCENTS = {0xff69e2f6, 0xff126ea1, 0xffb4dfff, 0xff65cfeb,
            0xff9cebc0, 0xffe1b7ff, 0xff9b5d18, 0xffd2e2fb, 0xfffb3acc, 0xffe16d36};
    private final JSONObject values;

    WidgetSettings() { this(new JSONObject()); }
    private WidgetSettings(JSONObject values) { this.values = values; }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("skycast_widget_settings", Context.MODE_PRIVATE);
    }
    static WidgetSettings load(Context context, int id) {
        try { WidgetSettings result=decode(preferences(context).getString("widget_" + id, "{}"));
            if (!result.values.has("theme")) result.set("theme",new AppPrefs(context).appTheme());
            return result; }
        catch (ClassCastException e) { return new WidgetSettings(); }
    }
    static WidgetSettings decode(String value) {
        try { return new WidgetSettings(new JSONObject(value)); }
        catch (JSONException e) { return new WidgetSettings(); }
    }
    String encode() { return values.toString(); }
    void save(Context context, int id) {
        preferences(context).edit().putString("widget_" + id, values.toString()).apply();
    }
    static void remove(Context context, int id) {
        preferences(context).edit().remove("widget_" + id).apply();
        AppPrefs.deleteWidget(context, id);
    }
    void set(String key, Object value) {
        try { values.put(key, value); }
        catch (JSONException e) { throw new IllegalArgumentException("Invalid widget setting", e); }
    }
    String text(String key) { return values.optString(key, ""); }
    void resetThemeOverrides() {
        for(String key:new String[]{"background","foreground","accent","rainColor","amountColor", "dayColor", "conditionColor", "font", "density"}) values.remove(key);
    }
    void clear(String key) { values.remove(key); }
    boolean show(String key) { return showOption(key, !("source".equals(key) || "updated".equals(key) || "weekLabel".equals(key))); }
    private int range(String key, int fallback, int min, int max) {
        return Math.min(max, Math.max(min, values.optInt(key, fallback)));
    }
    int layout() { return range("layout", DEFAULT, DEFAULT, TODAY_ONLY); }
    int theme() { return range("theme", 0, 0, THEMES.length - 1); }
    int days() { return range("days", 7, 1, 7); }
    int textSize() { return range("textSize", 100, 70, 150); }
    int size(String element, int fallback) { return range("size_" + element, fallback, 7, 40); }
    int font() { return range("font", THEME_FONTS[theme()], 0, FONTS.length - 1); }
    int chosenIconStyle() {return range("iconStyle",0,0, ICON_STYLES.length - 1);}
    int iconStyle() { int style = chosenIconStyle(); return style == 0 ? THEME_ICONS[theme()] : style; }
    int density() { return range("density", THEME_DENSITY[theme()], 0, 2); }
    int align() { return range("align", 0, 0, 2); }
    boolean alternateOrder() { return values.optBoolean("alternateOrder", false); }
    boolean reverseWeek() { return values.optBoolean("reverseWeek", false); }
    boolean showOption(String key, boolean fallback) { return values.optBoolean(key, fallback); }
    int iconSize() { return range("iconSize", 85, 40, 150); }
    int opacity() { return range("opacity", 96, 0, 100); }
    int units() { return range("units", 0, 0, 2); }
    int refreshMinutes() {
        int value = values.optInt("refreshMinutes", 0);
        return value == 15 || value == 30 || value == 60 || value == 120 || value == 180 ? value : 0;
    }
    int refreshMinutes(AppPrefs app) {
        int minutes = refreshMinutes();
        return minutes == 0 ? app.refreshMinutes() : minutes;
    }
    boolean fahrenheit(AppPrefs app) { return units() == 0 ? app.fahrenheit() : units() == 2; }

    private int color(String key, int fallback) {
        String value = text(key).trim();
        if (value.matches("#?[0-9A-Fa-f]{6}")) {
            return Color.parseColor(value.startsWith("#") ? value : "#" + value);
        }
        return fallback;
    }
    int background() {
        int alpha = Math.round(255 * opacity() / 100f);
        return (alpha << 24) | (color("background", BACKGROUNDS[theme()]) & 0x00ffffff);
    }
    int foreground() { return color("foreground", TEXT[theme()]); }
    int accent() { return color("accent", ACCENTS[theme()]); }
    int rainColor() { return color("rainColor", accent()); }
    int amountColor() { return color("amountColor", foreground()); }
    int dayColor() { return color("dayColor", accent()); }
    int conditionColor() { return color("conditionColor", accent()); }

    String source() {
        String source = text("provider");
        for (String id : WeatherApi.MODEL_IDS) if (id.equals(source)) return source;
        return "";
    }
    WeatherApi.Place place() {
        JSONObject stored = values.optJSONObject("place");
        if (stored == null) return null;
        double lat = stored.optDouble("lat", Double.NaN), lon = stored.optDouble("lon", Double.NaN);
        String name = stored.optString("name");
        if (!Double.isFinite(lat) || Math.abs(lat) > 90 || !Double.isFinite(lon)
                || Math.abs(lon) > 180 || name.isEmpty()) return null;
        WeatherApi.Place place = new WeatherApi.Place();
        place.lat = lat;
        place.lon = lon;
        place.name = name;
        place.timezone = stored.optString("timezone");
        return place;
    }
    void setPlace(WeatherApi.Place place) {
        if (place == null) {
            values.remove("place");
            return;
        }
        try {
            set("place", new JSONObject().put("lat", place.lat).put("lon", place.lon)
                    .put("name", place.name).put("timezone", place.timezone));
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid location", e); }
    }
    boolean independent() { return place() != null || !source().isEmpty() || refreshMinutes() != 0; }

    AppPrefs forecastPrefs(Context context, int id) {
        AppPrefs app = new AppPrefs(context);
        if (!independent()) return app;
        WeatherApi.Place place = place();
        AppPrefs scoped = AppPrefs.forWidget(context, id);
        String source = source();
        scoped.setWidgetQuery(place == null ? app.lat() : place.lat,
                place == null ? app.lon() : place.lon, place == null ? app.city() : place.name,
                place == null ? app.timezone() : place.timezone,
                source.isEmpty() ? app.model() : source, refreshMinutes(app));
        if (place == null && source.isEmpty() && scoped.lastSuccess() == 0) {
            String snapshot = app.get("lastForecast", "");
            if (!snapshot.isEmpty()) scoped.saveForecast(snapshot, app.lastSuccess());
        }
        return scoped;
    }
}
