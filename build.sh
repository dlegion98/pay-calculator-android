#!/bin/bash
set -e
cd "$(dirname "$0")"
A23=/usr/lib/android-sdk/platforms/android-23/android.jar
A36=/root/sdk/android-36.jar
rm -rf build && mkdir -p build/gen build/classes
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -A assets -I $A23 -F build/res.apk
if ! javac -nowarn -source 8 -target 8 -bootclasspath $A36 -encoding UTF-8 -d build/classes -sourcepath src:build/gen $(find src build/gen -name '*.java') > build/javac.log 2>&1; then
  grep -v -E "^warning|Picked up|bootstrap|^Note:" build/javac.log
  echo "COMPILE FAILED"; exit 1
fi
dalvik-exchange --dex --min-sdk-version=26 --output=build/classes.dex build/classes
cp build/res.apk build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
# Signing password: $PAYCALC_KEY_PASS, or a local (git-ignored) keystore.properties with "password=..."
KEYPASS="${PAYCALC_KEY_PASS:-$( [ -f keystore.properties ] && sed -n 's/^password=//p' keystore.properties )}"
[ -n "$KEYPASS" ] || { echo "Set PAYCALC_KEY_PASS or create keystore.properties (password=...)"; exit 1; }
[ -f release.jks ] || keytool -genkeypair -keystore release.jks -storepass "$KEYPASS" -keypass "$KEYPASS" -alias paycalc -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pay Calculator, O=Dayo Adekoya, C=GB" 2>/dev/null
apksigner sign --ks release.jks --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" --out build/PayCalculator.apk build/aligned.apk
apksigner verify build/PayCalculator.apk && echo "BUILD OK"
ls -la build/PayCalculator.apk
