#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/com/skycast/pro/RainNowcast.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/RadarMotion.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/RainNowcastEngine.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/Forecast.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/TodayHours.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/WidgetSizing.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/ForecastQuality.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/ForecastMath.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/RainAgreement.java" \
  "$ROOT/app/src/main/java/com/skycast/pro/RainBrief.java" \
  "$ROOT/tools/ForecastMathTest.java" \
  "$ROOT/tools/WidgetForecastTest.java" \
  "$ROOT/tools/RainAgreementTest.java" \
  "$ROOT/tools/RadarMotionTest.java" \
  "$ROOT/tools/TodayHoursTest.java" \
  "$ROOT/tools/WidgetSizingTest.java"
java -cp "$OUT" ForecastMathTest
java -cp "$OUT" WidgetForecastTest
java -cp "$OUT" RainAgreementTest
java -cp "$OUT" RadarMotionTest
java -cp "$OUT" TodayHoursTest
java -cp "$OUT" WidgetSizingTest
