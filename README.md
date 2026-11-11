# SkyCast Pro Next — 2.0.0-beta.1 (Rain Nowcasting)

A keyless Android weather app (Android 8+, `com.skycast.pro.next`). This build includes a **best-effort rain nowcasting layer** and an in-app live radar preview alongside the existing hourly / seven-day forecast and customizable widgets.

## Rain nowcasting in one integrated update

1. **Live historical radar tiles:** RainViewer Weather Maps API (`https://api.rainviewer.com/public/weather-maps.json`). Loads the **latest two historical frames** over the selected city, verifies timestamp and location coverage with the provider coverage mask, and displays real radar images in the app. Last two frames can be replayed in the native preview. Provider attribution is visible. The free public API does **not** offer future radar frames.
2. **Motion-based 0–2 h estimate:** Tests coherence between successive imagery; tracks the apparent movement of precipitation cells across a compact raster grid; extrapolates potential timing and relative echo intensity in 10-minute bins. Reports **broad 15-minute arrival windows** and qualitative echo strength, *not* radar-calibrated rainfall millimeters or guaranteed minute-precise rain.
3. **Independent station observation:** Keyless NOAA AviationWeather METAR API (where a Pakistan airport reporting station exists within 60 km). Distinguishes actual station rain observations from local predictions, rejects observations older than 105 minutes, and shows station distance. A nearby airport is not a household rain sensor.
4. **Forecast-model fusion:** Keeps existing multi-model rain agreement and current Open-Meteo hourly rain totals / percentages. Radar and station evidence are supplementary. If radar is absent, out of range, stale, sparse, incoherent, or its coverage is unverified, the app falls back to explicitly labeled hourly *model* forecasts and **does not invent radar-based arrivals**.
5. **Retrospective verification:** Stores bounded, private radar-echo and nearby-airport verification counts (hits, misses, false alarms), only where subsequent usable data exists. These are diagnostic proxy checks; they are not calibrated confidence or validated address-level accuracy.
6. **Notifications:** Opt-in radar-echo approach alerts only where fresh covered radar imagery supports a moving rain cell, with conservative on-screen labels. Existing optional model rain forecasts remain available.
7. **Live previews:** An in-app RainViewer frame preview shows generation timestamp, freshness and source credits; a user-invoked embedded official PMD radar viewer is provided for Pakistan, together with an external-browser fallback. **PMD's public page may show old images** and does not constitute permission for programmatic ingestion. Embedded viewing depends on the official site's WebView compatibility.

### Refresh, battery and data freshness

The 15-minute rain-priority periodic refresh **requests** are subject to Android JobScheduler deferral. Radar requests are cached for 10 minutes to respect free-service usage; imagery refresh rate depends on source availability. Hourly model products do not become minute-level observations by rechecking more often. Timestamps, coverage and stale-data warnings take priority over apparent precision. Live frame downloads occur on background threads, with a failure-tolerant model fallback; no API key is required.

## Widget and visual customization retained

The 4×2 widget has separate **Live**, **Today**, and **Seven Days** sections. Rain probability (%) and precipitation (mm) display independently, with unavailable data marked —. Rain timing gives radar-assisted wording only for valid, recent imagery, otherwise a labeled hourly model forecast. Other layouts remain selectable. All 10 app-wide themes, per-widget overrides, 10 icon treatments plus automatic theme-linked icons, text and rain color **HSV sliders**, text/element sizes, icon sizes, typefaces, density and individual widget location/provider settings remain present.

## Build and release

GitHub Actions: [Actions → Build SkyCastPro Next](https://github.com/amjadrajamemon/Sky/actions). The workflow runs pure Java tests, JSON/provider parser tests, Android lint, Android SDK 35 debug APK compilation and signature verification, then uploads `SkyCastPro-Next-v2.0.0-beta.1-debug` as a downloadable artifact.

Local build (JDK 17, Gradle 8.9, Android SDK 35):

```sh
gradle --no-daemon :app:lintDebug :app:assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`. The debug build is not a Play Store production release and may have a signing key different from previously installed versions.

## Data use / accuracy / publishing

- [RainViewer API documentation and terms](https://www.rainviewer.com/api.html): no-key public imagery is intended for personal, educational and small-scale community use, **not unrestricted commercial distribution**. Obtain provider permission/license for broader publication or commercial distribution. Attribution is mandatory.
- [RainViewer transition FAQ](https://www.rainviewer.com/api/transition-faq.html): future nowcast frames were discontinued from the free API on 1 January 2026, as were many color palettes. This app creates its own rudimentary extrapolation, not a provider-issued future radar product.
- [NOAA AviationWeather API](https://aviationweather.gov/data/api/): METAR reports are often hourly, may be absent, and may not represent neighborhood rainfall.
- [PMD Islamabad official radar](https://ffd.pmd.gov.pk/radar?site=islamabad): use only with timestamp checks; official warning and hazard instructions supersede this app.
- Radar reflectivity colors are **not directly a calibrated rainfall rate** in the free tiles. Do not equate a radar echo level with millimeters per hour. Confidence terms remain qualitative and provisional until extensive real-world verification.

No key, server deployment, GPS upload, account or tracking SDK is required. Nowcast verification records and downloaded preview frames are local device cache. Public data services may change or deny access without notice. The Android widget's real-world fit, the public radar's coverage at each location, and PMD embedded-page behavior need on-device testing.
