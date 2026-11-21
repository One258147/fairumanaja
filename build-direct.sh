#!/bin/bash
# Fairumanējā 全新简单版 1.4.0 — direct build without Gradle daemon.
# v1.4: package mapping is now read from each AAR's own AndroidManifest.xml.
# The hand-written table it replaced had 16 wrong entries (appcompat-resources
# -> androidx.appcompat instead of androidx.appcompat.resources, fragment ->
# androidx.fragment.app instead of androidx.fragment, lifecycle-* collapsed
# to androidx.lifecycle, viewpager2 -> androidx.viewpager2.widget, ...), so
# those libraries' R classes never made it into the dex -> NoClassDefFoundError
# at startup (v1.3 crash: androidx/appcompat/resources/R$drawable missing).
# v1.3: MainActivity.onCreate no longer wraps super.onCreate in try/catch
# (v1.2: any pre-super throw skipped super.onCreate -> SuperNotCalledException
# that also overwrote the original crash in crash.log).
# v1.1 fix: aapt2 link --extra-packages takes ONE package per flag. Passing a
# comma-joined list made aapt2 emit a single bogus R.java whose package was
# the whole comma string, so NO library R classes (R$styleable etc.) were
# compiled into the dex -> NoClassDefFoundError at startup -> instant crash.
# v1.2 passes --extra-packages once per package and aborts if any generated
# R.java path contains a comma.
# The sandbox blocks Java TCP (Gradle daemon protocol), so this pipeline
# uses aapt2 + kotlinc + d8 + zipalign + apksigner directly. All downloads
# use curl (Python/curl TCP works; only Java TCP is blocked).
# Material Design 3 is REAL here: material:1.13.0 + AppCompat 1.7.0 +
# RecyclerView 1.3.2 AARs are resolved from Google Maven and linked with aapt2.
# Usage: ./build-direct.sh   (run from ~/workspace/fairumanaja)
set -e
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
# kotlinc 启动器默认只给 -Xmx256M，代码量到 v1.7.0 规模会在 PSI 加载时 OOM；
# 这里默认给 2G（外部已设置 JAVA_OPTS 时不覆盖）。
export JAVA_OPTS="${JAVA_OPTS:--Xmx2g}"
# Force UTF-8 for all JVM tools (kotlinc/javac): the VM locale is POSIX, and
# a non-UTF-8 default charset can mojibake non-ASCII string constants.
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
SDK=/home/hatch/Android/Sdk
ANDROID_JAR=$SDK/platforms/android-34/android.jar
AAPT2=$SDK/build-tools/34.0.0/aapt2
D8=$SDK/build-tools/34.0.0/d8
ZIPALIGN=$SDK/build-tools/34.0.0/zipalign
APKSIGNER=$SDK/build-tools/34.0.0/apksigner
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
STDLIB=/home/hatch/kotlin/kotlinc/lib/kotlin-stdlib.jar
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT=$ROOT/build-direct
AARS=$ROOT/build-direct/aars
APK_OUT=$ROOT/app/build/outputs/apk/debug/app-debug.apk

rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/libjars" "$OUT/libres" \
  "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk" "$OUT/compiled-app.zip" \
  "$OUT/compiled-lib-"*.zip "$OUT/libs.json" "$OUT/clean-jars.json" "$OUT/cleanjars"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$AARS" "$(dirname "$APK_OUT")"

echo "== [0/7] resolve Material 3 / AndroidX AARs from Google Maven =="
if [ ! -f "$AARS/manifest.json" ]; then
  python3 "$ROOT/tools/resolve-aars.py" "$AARS"
fi

echo "== [1/7] extract AARs (classes.jar + res + package) =="
python3 - "$AARS" "$OUT" << 'PY'
import json, os, sys, zipfile, xml.etree.ElementTree as ET
aars_dir, out = sys.argv[1], sys.argv[2]
man = json.load(open(os.path.join(aars_dir, "manifest.json")))
os.makedirs(os.path.join(out, "libjars"), exist_ok=True)
os.makedirs(os.path.join(out, "libres"), exist_ok=True)
pkgs, compiled, jars, res_pkgs = [], [], [], []
for e in man:
    f = e["file"]
    if not os.path.exists(f):
        f = os.path.join(aars_dir, os.path.basename(f))
    name = f"{e['artifact']}-{e['version']}"
    if e["type"] == "jar":
        jars.append(f)
        continue
    z = zipfile.ZipFile(f)
    # Package straight from this AAR's own AndroidManifest.xml (plain text
    # XML in AARs, NOT binary). The hand-written mapping this replaced had
    # 16 wrong entries, which silently dropped those libraries' R classes
    # from the dex -> NoClassDefFoundError at startup (v1.3 crash). Never
    # hard-code packages again: aapt2 link --extra-packages must list
    # exactly the manifest package of every library.
    raw = z.read("AndroidManifest.xml")
    try:
        pkg = ET.fromstring(raw).get("package")
    except Exception as ex:
        print(f"  ERROR: cannot parse AndroidManifest.xml of {e['artifact']}: {ex}")
        sys.exit(1)
    if not pkg:
        print(f"  ERROR: no package in AndroidManifest.xml of {e['artifact']}")
        sys.exit(1)
    pkgs.append(pkg)
    dest_jar = os.path.join(out, "libjars", name + ".jar")
    if "classes.jar" in z.namelist():
        open(dest_jar, "wb").write(z.read("classes.jar"))
        jars.append(dest_jar)
    resdir = os.path.join(out, "libres", name)
    has_res = False
    for n in z.namelist():
        if n.startswith("res/") and not n.endswith("/"):
            has_res = True
            p = os.path.join(resdir, n[4:])
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(z.read(n))
    # Keep the AAR manifest next to its res: aapt2 compile uses it to give
    # the library's flat resources their own package (without it, every
    # library's styleables collide in one package at link time).
    if "AndroidManifest.xml" in z.namelist():
        os.makedirs(resdir, exist_ok=True)
        open(os.path.join(resdir, "AndroidManifest.xml"), "wb").write(z.read("AndroidManifest.xml"))
    if has_res:
        compiled.append(resdir)
        res_pkgs.append(pkg)
json.dump({"packages": sorted(set(pkgs)), "res_packages": sorted(set(res_pkgs)), "libres": compiled, "jars": jars},
          open(os.path.join(out, "libs.json"), "w"), indent=1)
print(f"  libs: {len(man)} artifacts, {len(compiled)} with res, {len(jars)} jars, {len(set(pkgs))} packages")
# aapt2 cannot link two different styleables with the same name. Only two
# collide in the whole set: Carousel (material vs constraintlayout) and
# SearchView (material vs appcompat). This app uses neither Material's
# Carousel nor Material's SearchView (it uses AppCompat's SearchView), so
# drop Material's two declare-styleable blocks; the <attr> definitions stay.
import re
mp = os.path.join(out, "libres", "material-1.13.0", "values", "values.xml")
s = open(mp).read()
for _name in ("Carousel", "SearchView"):
    s2 = re.sub(r'  <declare-styleable name="' + _name + r'"[^>]*>.*?</declare-styleable>\n', '', s, flags=re.S)
    print(f"  stripped material styleable {_name}: {s != s2}")
    s = s2
open(mp, "w").write(s)
PY

echo "== [2/7] aapt2 compile resources (app + libraries) =="
"$AAPT2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/compiled-app.zip"
COMPILED_LIST="$OUT/compiled-app.zip"
i=0
for d in $(python3 -c "import json; print(' '.join(json.load(open('$OUT/libs.json'))['libres']))"); do
  i=$((i+1))
  "$AAPT2" compile --dir "$d" -o "$OUT/compiled-lib-$i.zip"
  COMPILED_LIST="$COMPILED_LIST $OUT/compiled-lib-$i.zip"
done

echo "== [3/7] aapt2 link =="
# --extra-packages takes ONE package per flag; repeat it per package.
EXTRA_PKGS_FLAGS=""
for _p in $(python3 -c "import json; print(' '.join(json.load(open('$OUT/libs.json'))['packages']))"); do
  EXTRA_PKGS_FLAGS="$EXTRA_PKGS_FLAGS --extra-packages $_p"
done
"$AAPT2" link -I "$ANDROID_JAR" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 21 --version-name 1.9.7 \
  --auto-add-overlay \
  -A "$ROOT/app/src/main/assets" \
  $EXTRA_PKGS_FLAGS \
  -o "$OUT/app-unsigned.apk" $COMPILED_LIST
# Guard: every library package must have its own R.java (v1.1 produced one
# bogus comma-package R.java and no library R classes -> startup crash;
# v1.3 had the right count but 13 packages were WRONG, so their R classes
# were generated under bogus packages and the real ones were missing).
if find "$OUT/gen" -name 'R.java' | grep -q ','; then echo "ERROR: bogus comma-package R.java generated"; exit 1; fi
python3 - "$OUT" << 'PY'
import json, os, sys
out = sys.argv[1]
libs = json.load(open(os.path.join(out, "libs.json")))
missing = [p for p in libs["packages"]
           if not os.path.exists(os.path.join(out, "gen", *p.split("."), "R.java"))]
if missing:
    print("ERROR: no R.java generated for packages:", ", ".join(missing))
    sys.exit(1)
n = sum(1 for _r, _d, fs in os.walk(os.path.join(out, "gen")) for f in fs if f == "R.java")
print(f"  R.java files: {n} ({len(libs['packages'])} library packages + app)")
PY

echo "== [4/7] kotlinc =="
CP="$ANDROID_JAR"
for j in $(python3 -c "import json; print(' '.join(json.load(open('$OUT/libs.json'))['jars']))"); do CP="$CP:$j"; done
# Drop duplicate plain annotation/collection jars when their -jvm twin is present.
CP=$(python3 -c "
import json
jars = json.load(open('$OUT/libs.json'))['jars']
names = {j.split('/')[-1] for j in jars}
drop = set()
if 'annotation-jvm-1.8.1.jar' in names: drop.add('annotation-1.8.1.jar')
if 'collection-jvm-1.4.2.jar' in names: drop.add('collection-1.4.2.jar')
print('$ANDROID_JAR:' + ':'.join(j for j in jars if j.split('/')[-1] not in drop))
")
"$KOTLINC" -jvm-target 17 -nowarn -cp "$CP" -d "$OUT/classes" \
  $(find "$ROOT/app/src/main/java" -name '*.kt') \
  $(find "$OUT/gen" -name '*.java')

echo "== [4b/7] javac R.java (kotlinc resolves .java sources but emits no bytecode for them) =="
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -cp "$CP" -d "$OUT/classes" \
  $(find "$OUT/gen" -name '*.java')
# Guard: R$styleable classes must exist in classes/ or the app dies at
# startup with NoClassDefFoundError the first time AppCompat/Material reads
# a styleable (exactly the v1.1 crash).
if [ ! -f "$OUT/classes/androidx/appcompat/R\$styleable.class" ] || \
   [ ! -f "$OUT/classes/com/google/android/material/R\$styleable.class" ] || \
   [ ! -f "$OUT/classes/com/fairuman/filemanager/R.class" ]; then
  echo "ERROR: R classes not compiled"; exit 1
fi
echo "  R classes compiled OK"
# Guard: every R class referenced by any app/library class file must exist
# in classes/ (v1.3: androidx/appcompat/resources/R$drawable etc. were
# missing because their --extra-packages entries were wrong; the check
# above only covered R$styleable of two libraries and missed it).
# NOTE: references are read from the constant pool (parsed properly), NOT
# by regexing raw bytes: a raw scan also matches the 2-byte length prefix
# of CONSTANT_Utf8 entries (e.g. 0x30 '0') and reports phantom classes.
python3 - "$OUT" << 'PY'
import json, os, re, struct, sys, zipfile
out = sys.argv[1]
libs = json.load(open(os.path.join(out, "libs.json")))
r_pat = re.compile(r'(?:[A-Za-z][A-Za-z0-9_]*/)+R\$[A-Za-z][A-Za-z0-9_]*\Z')
def utf8_strings(data):
    if len(data) < 10 or data[:4] != b'\xca\xfe\xba\xbe':
        return []
    n, = struct.unpack_from('>H', data, 8)
    p = 10
    out_s = []
    i = 1
    while i < n:
        if p >= len(data):
            break
        tag = data[p]
        p += 1
        if tag == 1:
            ln, = struct.unpack_from('>H', data, p)
            p += 2
            out_s.append(data[p:p+ln].decode('utf-8', 'replace'))
            p += ln
        elif tag in (7, 8, 16, 19, 20):
            p += 2
        elif tag == 15:
            p += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            p += 4
        elif tag in (5, 6):
            p += 8
            i += 1
        else:
            break
        i += 1
    return out_s
refs = set()
def scan_bytes(data):
    for s in utf8_strings(data):
        if r_pat.match(s) and not s.startswith('android/R$'):
            refs.add(s)  # framework R is provided by the OS at runtime
for root, _d, files in os.walk(os.path.join(out, "classes")):
    for fn in files:
        if fn.endswith('.class'):
            with open(os.path.join(root, fn), 'rb') as fh:
                scan_bytes(fh.read())
drop = {'annotation-1.8.1.jar', 'collection-1.4.2.jar'}  # superseded by -jvm twins, not dexed
for j in libs["jars"]:
    if os.path.basename(j) in drop:
        continue
    try:
        z = zipfile.ZipFile(j)
    except Exception:
        continue
    for n in z.namelist():
        if n.endswith('.class'):
            scan_bytes(z.read(n))
missing = sorted(r for r in refs if not os.path.exists(os.path.join(out, "classes", r + '.class')))
if missing:
    print("ERROR: R classes referenced by code but missing from classes/:")
    for m in missing:
        print("  " + m)
    sys.exit(1)
print(f"  R-reference check OK: {len(refs)} referenced R classes all present")
PY

echo "== [5/7] d8 dex =="
(cd "$OUT/classes" && "$JAVA_HOME/bin/jar" cf "$OUT/classes.jar" .)
# d8 (R8 8.2.2) NPEs on module-info.class / META-INF/versions entries in
# multi-release jars. Strip them from library jars before dexing.
export FAIRU_ROOT="$ROOT"
python3 - "$OUT" << 'PY'
import json, os, sys, zipfile
out = sys.argv[1]
jars = json.load(open(os.path.join(out, "libs.json")))["jars"]
names = {j.split('/')[-1] for j in jars}
drop = set()
if 'annotation-jvm-1.8.1.jar' in names: drop.add('annotation-1.8.1.jar')
if 'collection-jvm-1.4.2.jar' in names: drop.add('collection-1.4.2.jar')
clean = os.path.join(out, "cleanjars")
os.makedirs(clean, exist_ok=True)
out_jars = []
for j in jars:
    base = os.path.basename(j)
    if base in drop:
        continue
    dst = os.path.join(clean, base)
    zin = zipfile.ZipFile(j)
    zout = zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED)
    for item in zin.infolist():
        if item.filename == "module-info.class" or item.filename.startswith("META-INF/versions/"):
            continue
        zout.writestr(item, zin.read(item.filename))
    zout.close()
    out_jars.append(dst)
json.dump(out_jars, open(os.path.join(out, "clean-jars.json"), "w"))
print(f"  cleaned {len(out_jars)} jars")
# d8 (R8 8.2.2-dev) also NPEs on MethodParameters attributes with a null
# parameter name (synthetic this$0 params in AndroidX classes, e.g.
# ActionBarDrawerToggle$1). Strip them with tools/strip_method_params.py.
import subprocess
for _j in out_jars:
    _tmp = _j + ".stripped"
    subprocess.run(["python3", os.path.join(os.environ.get("FAIRU_ROOT", "."), "tools/strip_method_params.py"), _j, _tmp], check=True)
    os.replace(_tmp, _j)
print("  stripped MethodParameters from library jars")
PY
DEX_INPUTS="$OUT/classes.jar $STDLIB"
for j in $(python3 -c "import json; print(' '.join(json.load(open('$OUT/clean-jars.json'))))"); do DEX_INPUTS="$DEX_INPUTS $j"; done
"$D8" --release --min-api 24 --lib "$ANDROID_JAR" \
  --output "$OUT/dex" $DEX_INPUTS

echo "== [6/7] add dex + align =="
cd "$OUT/dex" && zip -q -r "$OUT/app-unsigned.apk" classes*.dex && cd "$ROOT"
"$ZIPALIGN" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "== [7/7] sign =="
if [ ! -f "$ROOT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$ROOT/debug.keystore" -alias fairu \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Fairumanaja, OU=Fairumanaja, O=Fairumanaja, C=CN" 2>/dev/null
fi
"$APKSIGNER" sign --ks "$ROOT/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --out "$APK_OUT" "$OUT/app-aligned.apk"
"$APKSIGNER" verify --verbose "$APK_OUT"
echo "== APK built: $APK_OUT =="
ls -lh "$APK_OUT"
"$AAPT2" dump badging "$APK_OUT" | head -12
