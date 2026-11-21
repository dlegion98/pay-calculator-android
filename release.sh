#!/bin/bash
# Publish a new version: builds the APK, copies it to releases/, writes releases/latest.json
# (which the app's updater reads), then commits and pushes.
# Usage: ./release.sh "What's new in this version"
set -e
cd "$(dirname "$0")"
NOTES="${1:-Bug fixes and improvements.}"
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
CODE=$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
./build.sh
mkdir -p releases
cp build/PayCalculator.apk releases/PayCalculator.apk
SIZE=$(stat -c %s releases/PayCalculator.apk)
python3 - "$VERSION" "$CODE" "$SIZE" "$NOTES" > releases/latest.json <<'PY'
import json, sys
v, code, size, notes = sys.argv[1:5]
print(json.dumps({"version": v, "versionCode": int(code), "size": int(size), "notes": notes,
  "apk": "https://raw.githubusercontent.com/dlegion98/pay-calculator-android/main/releases/PayCalculator.apk"}, indent=2))
PY
git add -A
git commit -m "Release $VERSION"
git push origin main
echo "Released $VERSION"
