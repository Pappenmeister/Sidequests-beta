#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
cd "$(dirname "$0")"

for cmd in git gh gradle; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "FEHLER: '$cmd' fehlt. Installiere bei Bedarf mit: pkg install git gh gradle"
    exit 1
  fi
done

if ! gh auth status >/dev/null 2>&1; then
  echo "Du bist noch nicht bei GitHub angemeldet."
  echo "Starte jetzt: gh auth login"
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
  echo "FEHLER: Android SDK nicht gefunden. Erst ANDROID_HOME/ANDROID_SDK_ROOT korrekt setzen."
  exit 1
fi
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

VERSION="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
if [ -z "$VERSION" ]; then
  echo "FEHLER: versionName konnte nicht aus app/build.gradle.kts gelesen werden."
  exit 1
fi
TAG="v$VERSION"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "1/4: Baue installierbare Debug-APK für SideQuests $VERSION ..."
gradle --no-daemon :app:assembleDebug
if [ ! -f "$APK" ]; then
  echo "FEHLER: APK nicht gefunden: $APK"
  exit 1
fi

mkdir -p dist
cp "$APK" "dist/SideQuests-$TAG-debug.apk"
APK="dist/SideQuests-$TAG-debug.apk"

echo
echo "2/4: Git-Repository vorbereiten ..."
if [ ! -d .git ]; then
  git init
fi
git branch -M main
git add README.md RELEASE_NOTES.md settings.gradle.kts build.gradle.kts gradle.properties .gitignore app build-termux.sh publish-release.sh

if ! git diff --cached --quiet; then
  git commit -m "Prepare SideQuests $TAG"
elif ! git rev-parse --verify HEAD >/dev/null 2>&1; then
  echo "FEHLER: Kein erster Commit möglich; prüfe die Projektdateien."
  exit 1
fi

echo
echo "3/4: GitHub-Repository prüfen/erstellen ..."
if ! git remote get-url origin >/dev/null 2>&1; then
  read -r -p "Neuer GitHub-Repository-Name [SideQuests]: " REPO_NAME
  REPO_NAME="${REPO_NAME:-SideQuests}"
  read -r -p "Sichtbarkeit: public oder private [public]: " VISIBILITY
  VISIBILITY="${VISIBILITY:-public}"
  case "$VISIBILITY" in
    public) VIS_FLAG="--public" ;;
    private) VIS_FLAG="--private" ;;
    *) echo "FEHLER: Bitte 'public' oder 'private' eingeben."; exit 1 ;;
  esac
  gh repo create "$REPO_NAME" "$VIS_FLAG" --source . --remote origin --push
else
  git push -u origin main
fi

echo
echo "4/4: GitHub-Release $TAG veröffentlichen ..."
if gh release view "$TAG" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK" --clobber
else
  gh release create "$TAG" "$APK" --title "SideQuests $TAG" --notes-file RELEASE_NOTES.md
fi

echo
echo "FERTIG: Repository und Release wurden verarbeitet."
gh repo view --web
echo "APK-Asset: $APK"
