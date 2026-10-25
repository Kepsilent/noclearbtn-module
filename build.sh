#!/usr/bin/env bash
# RecentsKeeper 4KB-class one-shot build.
# Requires: JDK 17+, Android SDK (platform 34), and a lib dir with:
#   api-102.jar (libxposed API), apksig-jar (AOSP apksig classes), r8.jar (R8 shrinker)
#   LIB_DIR default: ../rnc-min/lib (the author's build environment)
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-/home/user/Android/Sdk}"
KEY_DIR="${KEY_DIR:-../rnc-min}"
LIB_DIR="${LIB_DIR:-../rnc-min/lib}"
JDK="${JAVA_HOME:-$(ls -d "$LIB_DIR"/jdk-17* 2>/dev/null | head -1)}"
[ -z "$JDK" ] && JDK="$(dirname "$(dirname "$(command -v javac)")")"
AAPT2="$(ls "$SDK"/build-tools/*/aapt2 | tail -1)"
APKSIGNER="$(ls "$SDK"/build-tools/*/apksigner | tail -1)"
PKG="io.github.kepsilent.noclearbtn"

rm -rf build && mkdir -p build/classes build/r8 build/verify
echo "[1/6] javac --release 8"
"$JDK/bin/javac" --release 8 \
  -classpath "$SDK/platforms/android-34/android.jar:$LIB_DIR/api-102.jar" \
  -d build/classes \
  app/src/main/java/io/github/kepsilent/noclearbtn/NoClearBtnHook.java
"$JDK/bin/jar" cf build/classes.jar -C build/classes .

echo "[2/6] R8 shrink"
printf -- "-keep class %s.NoClearBtnHook\n-dontwarn\n" "$PKG" > build/keep.pro
"$JDK/bin/java" -cp "$LIB_DIR/r8.jar" com.android.tools.r8.R8 \
  --release \
  --lib "$SDK/platforms/android-34/android.jar" \
  --lib "$LIB_DIR/api-102.jar" \
  --pg-conf build/keep.pro \
  --output build/r8 build/classes.jar

echo "[3/6] aapt2 compile + link"
"$AAPT2" compile --dir app/src/main/res -o build/res.zip
"$AAPT2" link -o build/unsigned.apk \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$SDK/platforms/android-34/android.jar" build/res.zip

echo "[4/6] zip reassembly (arsc STORED + deflate)"
python3 - <<'PY'
import zipfile, os
src = zipfile.ZipFile("build/unsigned.apk")
out = zipfile.ZipFile("build/asm.apk", "w", zipfile.ZIP_DEFLATED, compresslevel=9)
out.writestr("AndroidManifest.xml", src.read("AndroidManifest.xml"))
z = zipfile.ZipInfo("resources.arsc")
out.writestr(z, src.read("resources.arsc"), compress_type=zipfile.ZIP_STORED)
out.writestr("classes.dex", open("build/r8/classes.dex","rb").read())
for n in ("java_init.list", "module.prop", "scope.list"):
    out.writestr("META-INF/xposed/"+n, open("app/src/main/xposed/"+n, "rb").read())
out.close()
PY

echo "[4b/6] zipalign (force arsc 4-byte align)"
ZIPALIGN="$(ls "$SDK"/build-tools/*/zipalign | tail -1)"
"$ZIPALIGN" -p -f 4 build/asm.apk build/aligned.apk
"$ZIPALIGN" -c -p 4 build/aligned.apk && echo "alignment OK"

echo "[5/6] self-sign v2 (apksig block generator)"
"$JDK/bin/javac" -cp "$LIB_DIR/apksig-jar" -d build/verify scripts/GenFinal.java
"$JDK/bin/java" -cp "$LIB_DIR/apksig-jar:build/verify" GenFinal \
  build/aligned.apk "$KEY_DIR/key-ncb.der" "$KEY_DIR/cert-ncb.pem" build/block.bin

echo "[6/6] assemble + verify"
python3 - <<'PY'
import struct, os
content = open("build/block.bin","rb").read()
pair_len = len(content) + 4
size = len(content) + 36
block = struct.pack("<q", size) + struct.pack("<q", pair_len) \
      + struct.pack("<I", 0x7109871a) + content \
      + struct.pack("<q", size) + b"APK Sig Block 42"
src = open("build/aligned.apk","rb").read()
eocd_off = src.rfind(b"PK\x05\x06")
cd_off = struct.unpack("<I", src[eocd_off+16:eocd_off+20])[0]
entries, cd = src[:cd_off], src[cd_off:eocd_off]
eocd = bytearray(src[eocd_off:])
struct.pack_into("<I", eocd, 16, len(entries) + len(block))
open("release/NoClearBtn-v1.0.apk","wb").write(entries + block + cd + bytes(eocd))
print("size:", os.path.getsize("release/NoClearBtn-v1.0.apk"), "bytes")
PY
"$APKSIGNER" verify --verbose release/NoClearBtn-v1.0.apk | head -3
echo "DONE: release/NoClearBtn-v1.0.apk"
