package com.skycast.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WidgetConfigActivity extends Activity {
    private static final int[] REFRESH_INTERVALS = {0, 30, 60, 120, 180};
    private final ExecutorService search = Executors.newSingleThreadExecutor();
    private final Runnable updatePreview = this::refreshPreview;
    private final List<EditText> colorFields = new ArrayList<>();
    private WidgetSettings settings;
    private int widgetId;
    private int preset;
    private LinearLayout form;
    private FrameLayout preview;
    private TextView previewCaption;
    private Button location;
    private int previewWidth;
    private int previewHeight;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, result());
        preset = WeatherWidget.kindFor(this, widgetId);
        if (preset < 0) {
            finish();
            return;
        }
        settings = saved == null ? WidgetSettings.load(this, widgetId) : WidgetSettings.decode(saved.getString("settings", "{}"));
        build();
    }

    static Intent intent(Context context, int id) {
        return new Intent(context, WidgetConfigActivity.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setData(Uri.parse("skycast://widget/" + id))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
    }

    static PendingIntent pendingIntent(Context context, int id) {
        return PendingIntent.getActivity(context, id, intent(context, id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void openPicker(Activity activity) {
        Map<Integer, Integer> widgets = WeatherWidget.activeWidgets(activity);
        if (widgets.isEmpty()) {
            Toast.makeText(activity, "Add a SkyCast widget to your home screen first", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Integer> ids = new ArrayList<>(widgets.keySet());
        if (ids.size() == 1) {
            activity.startActivity(intent(activity, ids.get(0)));
            return;
        }
        String[] choices = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            WidgetSettings settings = WidgetSettings.load(activity, ids.get(i));
            String title = settings.text("title").trim();
            WeatherApi.Place place = settings.place();
            if (title.isEmpty() && place != null) title = place.name;
            String preset = new String[]{"Weather", "Weekly", "Compact"}[widgets.get(ids.get(i))];
            choices[i] = preset + " " + (i + 1) + (title.isEmpty() ? "" : " · " + title);
        }
        new AlertDialog.Builder(activity).setTitle(R.string.widget_customize)
                .setItems(choices, (dialog, index) -> activity.startActivity(intent(activity, ids.get(index)))).show();
    }

    private void build() {
        if (preview != null) preview.removeCallbacks(updatePreview);
        colorFields.clear();
        LinearLayout root = column();
        root.setBackgroundColor(0xff0b1727);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(label(getString(R.string.widget_customize), 22));
        ScrollView scroll = new ScrollView(this);
        form = column();
        form.setPadding(0, dp(4), 0, dp(10));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        int[] size = WeatherWidget.size(this, AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId), preset);
        previewWidth = Math.max(130, Math.min(size[0], getResources().getConfiguration().screenWidthDp - 36));
        previewHeight = Math.max(90, Math.min(320, size[1]));
        previewCaption = label("Preview", 14);
        form.addView(previewCaption);
        preview = new FrameLayout(this);
        LinearLayout.LayoutParams frame = new LinearLayout.LayoutParams(dp(previewWidth), dp(previewHeight));
        frame.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        form.addView(preview, frame);

        heading("Layout");
        select("Presentation", WidgetSettings.LAYOUTS, settings.layout(), value -> change("layout", value));
        slider("Forecast days", 1, 7, settings.days(), "", value -> change("days", value));
        select("Alignment", new String[]{"Left", "Center", "Right"}, settings.align(),
                value -> change("align", value));
        select("Spacing", new String[]{"Compact", "Balanced", "Spacious"}, settings.density(),
                value -> change("density", value));
        check("Reverse forecast days", "reverseWeek");

        heading("Sections");
        edit("Widget title", "City", "title", false);
        check("City title", "location");
        check("Live label", "liveLabel");
        check("Current temperature", "temperature");
        check("Current condition", "condition");
        check("Weather icons", "icons");
        check("Rain headline", "rain");
        check("Rain chance (%)", "chance");
        check("Rain amount (mm)", "amount");
        check("Hourly rain timing", "timing");
        check("Seven-day heading", "weekLabel");
        check("Daily highs and lows (when space allows)", "highsLows");
        check("Source", "source");
        check("Update time", "updated");
        check("Settings icon", "settingsIcon");

        heading("Appearance");
        select("Visual theme", WidgetSettings.THEMES, settings.theme(), value -> change("theme", value));
        select("Typeface", WidgetSettings.FONTS, settings.font(), value -> change("font", value));
        slider("Background opacity", 0, 100, settings.opacity(), "%", value -> change("opacity", value));
        slider("Overall text scale", 70, 150, settings.textSize(), "%", value -> change("textSize", value));
        slider("Weather icon scale", 40, 150, settings.iconSize(), "%", value -> change("iconSize", value));
        edit("Background #hex", "Theme default", "background", true);
        edit("Primary text #hex", "Theme default", "foreground", true);
        edit("Accent #hex", "Theme default", "accent", true);
        edit("Rain chance #hex", "Accent default", "rainColor", true);
        edit("Rain amount #hex", "Primary default", "amountColor", true);
        edit("Day heading #hex", "Accent default", "dayColor", true);
        edit("Condition #hex", "Accent default", "conditionColor", true);

        heading("Element sizes · sp");
        sizeSlider("City title", "title", 10, 7, 24);
        sizeSlider("Live tag", "liveLabel", 9, 7, 22);
        sizeSlider("Live temperature", "temperature", 25, 14, 40);
        sizeSlider("Conditions", "condition", 10, 7, 24);
        sizeSlider("Today's rain", "todayRain", 12, 8, 32);
        sizeSlider("Rain timing", "rainTiming", 9, 7, 24);
        sizeSlider("Week title", "weekLabel", 8, 7, 20);
        sizeSlider("Day names", "dayName", 8, 7, 20);
        sizeSlider("Day chance (%)", "dayChance", 9, 7, 24);
        sizeSlider("Day amount (mm)", "dayAmount", 9, 7, 24);
        sizeSlider("Day temperature", "dayTemp", 8, 7, 24);
        sizeSlider("Footer", "footer", 8, 7, 20);

        heading("Forecast");
        select("Temperature units", new String[]{"Follow app", "Celsius", "Fahrenheit"},
                settings.units(), value -> change("units", value));
        location = button(locationLabel(), this::chooseLocation);
        form.addView(location);
        String[] sources = new String[WeatherApi.MODEL_LABELS.length + 1];
        sources[0] = "Follow app";
        System.arraycopy(WeatherApi.MODEL_LABELS, 0, sources, 1, WeatherApi.MODEL_LABELS.length);
        int selected = 0;
        for (int i = 0; i < WeatherApi.MODEL_IDS.length; i++) {
            if (WeatherApi.MODEL_IDS[i].equals(settings.source())) selected = i + 1;
        }
        select("Provider", sources, selected, index -> change("provider", index == 0 ? "" : WeatherApi.MODEL_IDS[index - 1]));
        int refresh = 0;
        for (int i = 0; i < REFRESH_INTERVALS.length; i++) if (settings.refreshMinutes() == REFRESH_INTERVALS[i]) refresh = i;
        select("Refresh interval", new String[]{"Follow app", "30 minutes", "1 hour", "2 hours", "3 hours"},
                refresh, index -> change("refreshMinutes", REFRESH_INTERVALS[index]));

        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Reset", () -> {settings = new WidgetSettings(); build();}), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Cancel", this::finish), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Save", this::save), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        setContentView(root);
        refreshPreview();
    }

    private void change(String key, Object value) {
        settings.set(key, value);
        if (preview != null) {
            preview.removeCallbacks(updatePreview);
            preview.postDelayed(updatePreview, 80);
        }
    }

    private String locationLabel() {
        WeatherApi.Place place = settings.place();
        return "Location · " + (place == null ? "Follow app" : place.name);
    }

    private void chooseLocation() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("City name");
        new AlertDialog.Builder(this).setTitle("Widget location").setView(query)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Follow app", (dialog, action) -> {
                    settings.setPlace(null);
                    location.setText(locationLabel());
                    refreshPreview();
                })
                .setPositiveButton("Search", (dialog, action) -> {
                    String city = query.getText().toString().trim();
                    if (city.length() >= 2) searchLocation(city);
                }).show();
    }

    private void searchLocation(String query) {
        location.setEnabled(false);
        location.setText("Finding cities…");
        search.execute(() -> {
            try {
                List<WeatherApi.Place> places = new WeatherApi(this).searchCities(query);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    location.setEnabled(true);
                    location.setText(locationLabel());
                    if (places.isEmpty()) {
                        Toast.makeText(this, "No matching cities", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = places.stream().map(WeatherApi.Place::display).toArray(String[]::new);
                    new AlertDialog.Builder(this).setTitle("Choose a city").setItems(names, (dialog, index) -> {
                        settings.setPlace(places.get(index));
                        location.setText(locationLabel());
                        refreshPreview();
                    }).setNegativeButton("Cancel", null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    location.setEnabled(true);
                    location.setText(locationLabel());
                    Toast.makeText(this, "City search unavailable. Try again when connected.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void refreshPreview() {
        if (preview == null || isFinishing()) return;
        AppPrefs app = new AppPrefs(this);
        WeatherApi.Place place = settings.place();
        String city = place == null ? app.city() : place.name;
        Forecast f = place == null && settings.source().isEmpty() ? new ForecastStore(app).load() : null;
        if (f == null && settings.independent()) {
            AppPrefs cache = AppPrefs.forWidget(this, widgetId);
            double lat = place == null ? app.lat() : place.lat;
            double lon = place == null ? app.lon() : place.lon;
            String provider = settings.source().isEmpty() ? app.model() : settings.source();
            if (Double.compare(cache.lat(), lat) == 0 && Double.compare(cache.lon(), lon) == 0
                    && cache.model().equals(provider)) f = new ForecastStore(cache).load();
        }
        previewCaption.setText(f == null ? "Preview · sample weather" : "Preview");
        if (f == null) f = sample(city);
        try {
            String title = "Choose your city".equals(city) ? f.city : city;
            RemoteViews views = WidgetRenderer.render(this, f, title, settings, settings.fahrenheit(app),
                    true, preset, previewWidth, previewHeight);
            preview.removeAllViews();
            preview.addView(views.apply(this, preview));
        } catch (RuntimeException e) {
            preview.removeAllViews();
            preview.addView(label("Preview unavailable", 13));
        }
    }

    private static Forecast sample(String city) {
        Forecast f = new Forecast();
        f.city = city.equals("Choose your city") ? "Sample location" : city;
        f.source = "Sample forecast";
        f.retrievedAt = "10:42";
        f.timezone = java.time.ZoneId.systemDefault().getId();
        f.temperatureC = 21;
        f.weatherCode = 2;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            Forecast.Day day = new Forecast.Day();
            day.date = today.plusDays(i).toString();
            day.weatherCode = i == 1 || i == 3 ? 61 : 2;
            day.highC = 24 - i % 3;
            day.lowC = 14 + i % 2;
            day.rainProbability = i == 0 || i == 1 || i == 3 ? 77 : 10 + i * 3;
            day.rainMm = i == 0 || i == 1 || i == 3 ? 21.4 : (i % 3) * .8;
            f.days.add(day);
        }
        for (int i = 0; i < 48; i++) {
            Forecast.Hour h = new Forecast.Hour();
            h.time = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
                    .plusHours(i).toString();
            h.rainProbability = (i >= 2 && i <= 5) ? 77 : 12;
            h.rainMm = (i >= 2 && i <= 5) ? 2.3 : 0;
            f.hours.add(h);
        }
        return f;
    }

    private void save() {
        for (EditText field : colorFields) {
            String value = field.getText().toString().trim();
            if (!value.isEmpty() && !value.matches("#?[0-9A-Fa-f]{6}")) {
                field.setError("Use six hex digits, for example #143729");
                field.requestFocus();
                return;
            }
        }
        if (WeatherWidget.kindFor(this, widgetId) < 0) {
            finish();
            return;
        }
        settings.save(this, widgetId);
        if (!settings.independent()) {
            RefreshJob.cancelWidget(this, widgetId);
            AppPrefs.deleteWidget(this, widgetId);
        }
        WeatherWidget.updateOne(this, widgetId);
        RefreshJob.schedule(this);
        RefreshJob.requestWidget(this, widgetId, false);
        setResult(RESULT_OK, result());
        finish();
    }

    private Intent result() { return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private TextView label(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.WHITE);
        label.setPadding(0, dp(5), 0, dp(4));
        return label;
    }
    private void heading(String text) {
        TextView heading = label(text, 18);
        heading.setPadding(0, dp(16), 0, dp(4));
        heading.setTextColor(0xff7ee5df);
        form.addView(heading);
    }
    private Button button(String text, Runnable onClick) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setOnClickListener(view -> onClick.run());
        return button;
    }
    private void check(String title, String key) {
        CheckBox check = new CheckBox(this);
        check.setText(title);
        check.setChecked(settings.show(key));
        check.setOnCheckedChangeListener((button, value) -> change(key, value));
        form.addView(check);
    }
    private void select(String title, String[] choices, int selected, java.util.function.IntConsumer callback) {
        form.addView(label(title, 14));
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                callback.accept(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        form.addView(spinner);
    }
    private void slider(String title, int minimum, int maximum, int value, String suffix,
                        java.util.function.IntConsumer callback) {
        TextView caption = label(title + " · " + value + suffix, 14);
        form.addView(caption);
        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum - minimum);
        slider.setProgress(value - minimum);
        slider.setContentDescription(title);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int number = minimum + progress;
                caption.setText(title + " · " + number + suffix);
                callback.accept(number);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        form.addView(slider);
    }
    private void sizeSlider(String label, String key, int fallback, int min, int max) {
        slider(label, min, max, settings.size(key, fallback), "sp", value -> change("size_" + key, value));
    }
    private void edit(String title, String hint, String key, boolean color) {
        form.addView(label(title, 14));
        EditText field = new EditText(this);
        field.setText(settings.text(key));
        field.setSingleLine(true);
        field.setHint(hint);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(color ? 7 : 60)});
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {change(key, text.toString());}
            @Override public void afterTextChanged(Editable text) {}
        });
        if (color) colorFields.add(field);
        form.addView(field);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (settings != null) state.putString("settings", settings.encode());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (preview != null) preview.removeCallbacks(updatePreview);
        search.shutdownNow();
        super.onDestroy();
    }
}
