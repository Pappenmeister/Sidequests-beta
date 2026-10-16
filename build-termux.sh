#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
cd "$(dirname "$0")"

if ! command -v gradle >/dev/null 2>&1; then
  echo "FEHLER: Gradle fehlt. Installiere es mit: pkg install gradle"
  exit 1
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  for candidate in "$HOME/android-sdk" "$PREFIX/share/android-sdk" "$HOME/.android/sdk"; do
    if [ -d "$candidate" ]; then
      export ANDROID_HOME="$candidate"
      export ANDROID_SDK_ROOT="$candidate"
      break
    fi
  done
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "FEHLER: Android SDK wurde nicht gefunden."
  echo "Setze ANDROID_HOME auf den Pfad deines vorhandenen Android SDK."
  echo 'Beispiel: export ANDROID_HOME="$HOME/android-sdk"'
  exit 1
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

echo "Baue SideQuests Debug-APK ..."
gradle --no-daemon :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "FEHLER: Build beendet, aber APK nicht gefunden: $APK"
  exit 1
fi

mkdir -p dist
cp "$APK" "dist/SideQuests-v0.2.0-debug.apk"
echo
echo "BUILD ERFOLGREICH"
echo "APK: $(pwd)/dist/SideQuests-v0.2.0-debug.apk"
