#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON_JAR="${1:?Pass the org.json JSON-java test JAR path}"
JSOUP_JAR="${2:?Pass the jsoup 1.24.1 JAR path}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -cp "$JSON_JAR:$JSOUP_JAR" -d "$OUT" \
  "$ROOT/app/src/main/java/com/skycast/pro/RainNowcast.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/RainNowcastJson.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/StationObservation.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/Forecast.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/ForecastQuality.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/LocalForecastSkill.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/OpenMeteoForecastParser.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/GoogleWeatherParser.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/PublicWeatherParser.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/SourcePolicy.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/WidgetSizing.java" \
  "$ROOT/tools/ProviderRegressionTest.java" \
  "$ROOT/tools/NowcastSourceTest.java"
java -cp "$OUT:$JSON_JAR:$JSOUP_JAR" ProviderRegressionTest "${@:3}"
java -cp "$OUT:$JSON_JAR:$JSOUP_JAR" com.skycast.pro.NowcastSourceTest
