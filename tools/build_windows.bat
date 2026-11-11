@echo off
setlocal
cd /d "%~dp0\.."
where gradle >nul 2>nul
if errorlevel 1 (
  echo ERROR: Gradle 8.9 not found on PATH. Open the project in Android Studio or use the included GitHub Actions workflow.
  exit /b 1
)
if not defined ANDROID_HOME if not defined ANDROID_SDK_ROOT (
  echo ERROR: Android SDK missing. Set ANDROID_HOME or ANDROID_SDK_ROOT, or open project in Android Studio.
  exit /b 1
)
call gradle :app:lintDebug :app:assembleDebug
if errorlevel 1 exit /b %errorlevel%
echo Debug APK: app\build\outputs\apk\debug\app-debug.apk
