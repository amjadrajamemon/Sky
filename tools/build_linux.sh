#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v gradle >/dev/null || { echo 'Gradle 8.9 required; use GitHub Actions workflow if unavailable.'; exit 1; }
[[ -n "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]] || { echo 'Android SDK 35 required.'; exit 1; }
gradle :app:lintDebug :app:assembleDebug
printf '%s\n' 'Debug APK: app/build/outputs/apk/debug/app-debug.apk'
