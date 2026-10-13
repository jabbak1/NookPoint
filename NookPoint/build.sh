#!/usr/bin/env bash
# Builds NookPoint.apk with plain Android SDK command-line tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
[ -d "$SDK" ] || { echo "Android SDK not found at $SDK"; exit 1; }

BT=""
for d in $(ls -d "$SDK"/build-tools/*/ | sort -V -r); do
  d="${d%/}"
  if [ -x "$d/d8" ] && [ -x "$d/apksigner" ] && [ -x "$d/zipalign" ]; then BT="$d"; break; fi
done
[ -n "$BT" ] || { echo "No suitable build-tools"; exit 1; }

JAR=""
for d in $(ls -d "$SDK"/platforms/android-[0-9]*/ | sort -V -r); do
  if [ -f "${d}android.jar" ]; then JAR="${d}android.jar"; break; fi
done
[ -n "$JAR" ] || { echo "No android.jar"; exit 1; }
echo "build-tools: $BT"; echo "android.jar: $JAR"

rm -rf build; mkdir -p build/classes build/dex

echo "== javac"
javac -source 8 -target 8 -bootclasspath "$JAR" -encoding UTF-8 -Xlint:-options -nowarn \
  -d build/classes $(find src -name '*.java')

echo "== d8 (min API 7)"
"$BT/d8" --release --min-api 7 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

echo "== resources"
if [ -x "$BT/aapt" ]; then
  "$BT/aapt" package -f -0 ttf -M AndroidManifest.xml -S res -A assets -I "$JAR" -F build/app.unaligned
else
  "$BT/aapt2" compile --dir res -o build/res.zip
  "$BT/aapt2" link -o build/app.unaligned -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 7 --target-sdk-version 7 -A assets -0 .ttf build/res.zip
fi
(cd build/dex && zip -q -j ../app.unaligned classes.dex)

echo "== align + sign"
"$BT/zipalign" -f 4 build/app.unaligned build/app.aligned
KS="${KS:-$HOME/.nookpoint.jks}"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass nookpoint -keypass nookpoint -alias nook \
    -keyalg RSA -keysize 2048 -validity 8000 -dname "CN=NookPoint"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:nookpoint --key-pass pass:nookpoint \
  --ks-key-alias nook --min-sdk-version 7 \
  --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false \
  --out build/NookPoint.apk build/app.aligned
echo "Done: build/NookPoint.apk"
