# SkyCast Pro Next — 2.0.0-alpha.6

Android weather app, package `com.skycast.pro.next` (Android 8+, target API 34). This is a **source build candidate**, not a signed production release.

## 4 × 2 widgets

The Weather and Weekly widgets default to 4 × 2. The primary format separates **Live**, **Today's rain**, and the **seven-day outlook**. Days show the rain chance (%) and estimated rainfall amount (mm) independently, wherever supplied by the provider. Unavailable values display —, never a guessed zero. The Compact 2 × 2 widget defaults to live + today. The Android launcher may impose its own final cell dimensions.

Widget editor: tap ⚙, or select **Customize widgets** inside the app. Save per-widget location, forecast provider, units, refresh interval and appearance. The editor has a live preview, nine display layouts, eight gradient themes, five typefaces, color customization, display controls, per-element text sizes, opacity and horizontal alignment. The 7-day strip prioritizes both rainfall measurements; day icons and high/low temperatures appear when the launcher grants sufficient height. A five-day-only source cannot legitimately display seven days; choose a seven-day provider for the complete forecast.

Rain timing uses hourly source intervals only. It is a forecast signal, **not** minute-by-minute radar nowcasting. A sparse or unavailable provider does not produce fabricated minute-level rainfall onset/duration.

## Forecast providers

Open-Meteo Best Match/ECMWF/NOAA/DWD, MET Norway, a Google WeatherNext model via Open-Meteo, and experimental public MSN/AccuWeather pages. Third-party pages can change or block scraping. Provider names are not used to relabel unrelated forecasts. No user-supplied API key is required for current configured public sources. Provider reuse conditions apply; Open-Meteo's freely accessible API has noncommercial limitations. See providers' terms before public/commercial distribution.

## GitHub Actions build

The repository root contains `.github/workflows/android-build.yml`. It runs pure Java regression tests, provider parser regression tests, Android lint, Android SDK 35 debug compilation, APK signature verification, and publishes the APK in **Actions → latest successful Build SkyCastPro Next → Artifacts**.

To build from a terminal with JDK 17, Gradle 8.9 and Android SDK 35:

```sh
gradle --no-daemon :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The workflow uses GitHub's generated debug signing key. This may differ from any previous debug certificate; Android may require uninstalling a previously signed version before installing this APK. Back up any desired settings first.

## Verification scope

`bash tools/run-core-tests.sh` and `python3 tools/qa_static.py` perform pure Java and structural checks. A successful GitHub Actions run will establish cloud APK compilation and lint; real launcher sizing and refresh behavior must still be tested on an Android device.
