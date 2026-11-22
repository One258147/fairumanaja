#!/usr/bin/env python3
"""Resolve the Compose (CMP 1.12) AAR/JAR set for Fairuman\u0113j\u0101 v1.9.8.1.
Replaces tools/resolve-aars.py for the Compose build (the View stack is gone).
- Roots are a CURATED explicit list (proven by the /tmp probe build): exact
  artifact names + versions. A root's version is PINNED: no transitive dep
  may upgrade it.
- POM-walk for transitive deps (mainly AppCompat 1.7.0's closure + anything
  Miuix/Haze pull in), with KMP variant handling:
  * logical key is (group, base-name) where base-name strips -android/-jvm,
    so a base stub and its -android real artifact never both land on the
    classpath (first-seen variant wins; roots are added first)
  * download tries {a}-{v}.aar, {a}-{v}.jar, {a}-android-{v}.aar,
    {a}-jvm-{v}.jar and validates the artifact has >=1 real .class
    (KMP metadata stubs are rejected)
  * org.jetbrains.compose.* skipped (classes come from androidx.compose.*)
  * *-lint / *-test* skipped
- Shizuku section kept from resolve-aars.py.
- LiquidGlass (QWEA0) REMOVED: v1.9.8.1 uses HAZE (chrisbanes/haze 2.1.0).
Java TCP is blocked in this sandbox, but curl/Python TCP works.
Writes downloads to OUT dir + manifest.json.
"""
import os, sys, json, subprocess, re, zipfile, io
import xml.etree.ElementTree as ET

BASE = "https://dl.google.com/dl/android/maven2"
MC = "https://repo1.maven.org/maven2"
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "build-direct", "aars")
os.makedirs(OUT, exist_ok=True)

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def curl(url, dest):
    r = subprocess.run(["curl", "-sfL", "--max-time", "30", "-o", dest, url],
                       capture_output=True)
    return r.returncode == 0 and os.path.exists(dest) and os.path.getsize(dest) > 0

def fetch_pom(group, artifact, version):
    # try exact artifact name, then base name (strip -android/-jvm)
    cands = [artifact]
    b = base_name(artifact)
    if b != artifact:
        cands.append(b)
    for art in cands:
        dest = os.path.join(OUT, f"{group}.{art}-{version}.pom")
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            return dest
        for base in (BASE, MC):
            url = f"{base}/{group.replace('.', '/')}/{art}/{version}/{art}-{version}.pom"
            if curl(url, dest):
                return dest
    return None

def parse_props(root):
    props = {}
    p = root.find("m:properties", NS)
    if p is not None:
        for c in p:
            props[c.tag.split('}')[-1]] = (c.text or "").strip()
    return props

def subst(text, props, parent_props):
    if text is None:
        return None
    out = text
    for _ in range(5):
        changed = False
        for k, v in list(parent_props.items()) + list(props.items()):
            tok = "${" + k + "}"
            if tok in out:
                out = out.replace(tok, v)
                changed = True
        if not changed:
            break
    return out

def ver_tuple(v):
    m = re.match(r"^(\d+(?:\.\d+)*)(?:-(alpha|beta|rc)(\d*))?$", v or "")
    if not m:
        return (0,)
    nums = [int(x) for x in m.group(1).split(".")]
    nums += [0] * (4 - len(nums))
    rank = {"alpha": 0, "beta": 1, "rc": 2, "": 3}[m.group(2) or ""]
    return tuple(nums) + (rank, int(m.group(3) or 0))

def base_name(a):
    for suf in ("-android", "-jvm"):
        if a.endswith(suf):
            return a[: -len(suf)]
    return a

def norm_version(v):
    v = (v or "").strip()
    m = re.match(r"^\[([0-9][^,\]]*)\]$", v)
    if m:
        return m.group(1)
    m = re.match(r"^\[([0-9][^,\]]*),", v)
    if m:
        return m.group(1)
    return v

# ---- curated roots (probe-proven) ----
ROOTS = [
    # Compose runtime / ui / foundation / material3 (CMP 1.12.0-beta01)
    ("androidx.compose.runtime", "runtime-android", "1.12.0-beta01"),
    ("androidx.compose.runtime", "runtime-annotation-android", "1.12.0-beta01"),
    ("androidx.compose.runtime", "runtime-livedata", "1.12.0-beta01"),
    ("androidx.compose.runtime", "runtime-retain-android", "1.12.0-beta01"),
    ("androidx.compose.runtime", "runtime-saveable-android", "1.12.0-beta01"),
    ("androidx.compose.runtime", "runtime-tracing", "1.12.0-beta01"),
    ("androidx.compose.ui", "ui-android", "1.12.0-beta01"),
    ("androidx.compose.ui", "ui-geometry-android", "1.12.0-beta01"),
    ("androidx.compose.ui", "ui-graphics-android", "1.12.0-beta01"),
    ("androidx.compose.ui", "ui-text-android", "1.12.0-beta01"),
    ("androidx.compose.ui", "ui-unit-android", "1.12.0-beta01"),
    ("androidx.compose.ui", "ui-util-android", "1.12.0-beta01"),
    ("androidx.compose.foundation", "foundation-android", "1.12.0-beta01"),
    ("androidx.compose.foundation", "foundation-layout-android", "1.12.0-beta01"),
    ("androidx.compose.material3", "material3-android", "1.5.0-alpha22"),
    ("androidx.compose.animation", "animation-core-android", "1.12.0-beta01"),
    ("androidx.compose.material", "material-ripple-android", "1.12.0-alpha03"),
    # Activity / lifecycle / savedstate
    ("androidx.activity", "activity-compose", "1.10.1"),
    ("androidx.activity", "activity", "1.10.1"),
    ("androidx.lifecycle", "lifecycle-runtime-android", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-viewmodel-android", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-viewmodel-savedstate", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-livedata", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-livedata-core", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-common-jvm", "2.8.7"),
    ("androidx.lifecycle", "lifecycle-runtime-compose-android", "2.9.2"),
    ("androidx.savedstate", "savedstate-android", "1.5.0"),
    ("androidx.savedstate", "savedstate-compose-android", "1.5.0"),
    # androidx support libs
    ("androidx.arch.core", "core-common", "2.2.0"),
    ("androidx.arch.core", "core-runtime", "2.2.0"),
    ("androidx.core", "core", "1.15.0"),
    ("androidx.core", "core-viewtree", "1.0.0"),
    ("androidx.annotation", "annotation-jvm", "1.9.1"),
    ("androidx.collection", "collection-jvm", "1.7.0"),
    ("androidx.tracing", "tracing", "1.0.0"),
    ("androidx.interpolator", "interpolator", "1.0.0"),
    ("androidx.customview", "customview-poolingcontainer", "1.0.0"),
    ("androidx.emoji2", "emoji2", "1.5.0"),
    # Kotlin / coroutines
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.9.0"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.9.0"),
    # Miuix (real library, compose-miuix-ui/miuix 0.9.4)
    ("top.yukonga.miuix.kmp", "miuix-ui-android", "0.9.4"),
    ("top.yukonga.miuix.kmp", "miuix-core-android", "0.9.4"),
    ("top.yukonga.miuix.kmp", "miuix-squircle-android", "0.9.4"),
    ("androidx.navigationevent", "navigationevent-compose-android", "1.1.2"),
    ("com.materialkolor", "material-color-utilities-android", "5.0.1"),
    ("org.jetbrains.compose.material3", "material3-window-size-class-android", "1.12.0-alpha03"),
    # HAZE (liquid glass, Compose) — replaces QWEA0 Liquid-Glass-Android
    ("dev.chrisbanes.haze", "haze-android", "2.1.0"),
    ("dev.chrisbanes.haze", "haze-utils-android", "2.1.0"),
    ("dev.chrisbanes.haze", "haze-blur-android", "2.1.0"),
    # AppCompat (kept: BaseActivity stays AppCompatActivity so
    # AppCompatDelegate.setApplicationLocales keeps working)
    ("androidx.appcompat", "appcompat", "1.7.0"),
    # Material 1.13.0 (kept: MainActivity 的 3 个一次性系统对话框仍用
    # MaterialAlertDialogBuilder，另有 MaterialColors 取色；其余 View 根已删）。
    # 注意：drawerlayout / coordinatorlayout / recyclerview 不在上面，
    # 但仍留在 build-direct/aars/manifest.json 里——material 1.13.0 的
    # values/layout 在 aapt2 link 时要引用它们的 attr（drawerLayoutStyle、
    # layout_behavior），删了会 link 失败。它们只是链接期依赖，代码里不用。
    ("com.google.android.material", "material", "1.13.0"),
    ("androidx.core", "core-ktx", "1.13.1"),
]
SKIP = {("org.jetbrains.kotlin", "kotlin-stdlib"), ("org.jetbrains.kotlin", "kotlin-bom"),
        ("org.jetbrains", "annotations")}
SKIP_SUFFIX = ("-lint", "-test", "-test-junit4", "-test-manifest",
                "-test-manifest-lint", "-javadoc", "-sources")
SKIP_GROUPS = ("org.jetbrains.compose.",)

PINNED = {(g, base_name(a)): v for g, a, v in ROOTS}
resolved = {}   # (group, base) -> (artifact, version)
queue = []

def skip(g, a):
    if g.startswith(SKIP_GROUPS):
        return True
    return any(a.endswith(s) for s in SKIP_SUFFIX) or (g, a) in SKIP

def add(group, artifact, version):
    version = norm_version(version)
    if not version or not re.match(r"^[0-9]", version):
        return
    key = (group, base_name(artifact))
    if key in PINNED:
        version = PINNED[key]     # root version wins, never upgraded
    if key in resolved:
        if ver_tuple(version) > ver_tuple(resolved[key][1]):
            resolved[key] = (artifact, version)
            queue.append((group, artifact, version))
        return
    resolved[key] = (artifact, version)
    queue.append((group, artifact, version))

for g, a, v in ROOTS:
    add(g, a, v)

seen_pom = set()
while queue:
    group, artifact, version = queue.pop(0)
    if skip(group, artifact):
        continue
    pom = fetch_pom(group, artifact, version)
    if pom is None:
        print(f"  [no pom] {group}:{artifact}:{version}")
        continue
    key = (group, artifact, version)
    if key in seen_pom:
        continue
    seen_pom.add(key)
    try:
        root = ET.parse(pom).getroot()
    except Exception as e:
        print(f"  [bad pom] {pom}: {e}")
        continue
    props = parse_props(root)
    parent_props = {}
    parent = root.find("m:parent", NS)
    if parent is not None:
        pg = parent.findtext("m:groupId", namespaces=NS)
        pa = parent.findtext("m:artifactId", namespaces=NS)
        pv = parent.findtext("m:version", namespaces=NS)
        if pg and pa and pv:
            ppom = fetch_pom(pg, pa, pv)
            if ppom:
                try:
                    parent_props = parse_props(ET.parse(ppom).getroot())
                except Exception:
                    pass
    deps = root.find("m:dependencies", NS)
    if deps is None:
        continue
    for d in deps.findall("m:dependency", NS):
        scope = d.findtext("m:scope", namespaces=NS) or "compile"
        optional = d.findtext("m:optional", namespaces=NS) == "true"
        if scope in ("test", "provided") or optional:
            continue
        g = subst(d.findtext("m:groupId", namespaces=NS), props, parent_props)
        a = subst(d.findtext("m:artifactId", namespaces=NS), props, parent_props)
        v = subst(d.findtext("m:version", namespaces=NS), props, parent_props)
        if not g or not a or not v or "${" in v:
            continue
        if skip(g, a):
            continue
        add(g, a, v)

print(f"resolved {len(resolved)} artifacts")

def has_real_classes(path):
    try:
        z = zipfile.ZipFile(path)
        names = z.namelist()
        if path.endswith(".aar"):
            if "classes.jar" not in names:
                return False
            z2 = zipfile.ZipFile(io.BytesIO(z.read("classes.jar")))
            return any(n.endswith(".class") and n != "module-info.class"
                       for n in z2.namelist())
        return any(n.endswith(".class") and n != "module-info.class" for n in names)
    except Exception:
        return False

def download(group, artifact, version):
    variants = []
    for fn, kind in ((f"{artifact}-{version}.aar", "aar"),
                     (f"{artifact}-{version}.jar", "jar"),
                     (f"{artifact}-android-{version}.aar", "aar"),
                     (f"{artifact}-jvm-{version}.jar", "jar")):
        if fn not in [v[0] for v in variants]:
            variants.append((fn, kind))
    for fn, kind in variants:
        dest = os.path.join(OUT, fn)
        if os.path.exists(dest) and has_real_classes(dest):
            return fn, kind
        for base in (BASE, MC):
            tmp = dest + ".tmp"
            url = f"{base}/{group.replace('.', '/')}/{artifact}/{version}/{fn}"
            if curl(url, tmp):
                os.replace(tmp, dest)
                if has_real_classes(dest):
                    return fn, kind
                os.remove(dest)
    return None

manifest = []
# Merge with an existing manifest (e.g. the View stack from resolve-aars.py
# during the transitional Phase A build): keep existing entries, add new ones.
# Logical-key aware: a new entry REPLACES any existing entry with the same
# (group, base-name) — e.g. lifecycle-common-jvm 2.8.7 replaces the old
# lifecycle-common 2.6.2, otherwise d8 dies on duplicate classes.
_existing = os.path.join(OUT, "manifest.json")
if os.path.exists(_existing):
    try:
        manifest = json.load(open(_existing))
        print(f"merged {len(manifest)} existing manifest entries")
    except Exception:
        manifest = []
_have = {(e["group"], base_name(e["artifact"])) for e in manifest}
for (g, b), (a, v) in sorted(resolved.items()):
    if (g, b) in _have:
        # new (root-pinned or higher) entry wins over the old same-base one,
        # but never DOWNGRADE: a transitive dep asking for an older version
        # must not replace the newer one already in the manifest
        # (e.g. drawerlayout 1.0.0 must not replace 1.2.0 — material 1.13.0
        # needs the drawerLayoutStyle attr that 1.0.0 lacks).
        _old = [e for e in manifest
                if (e["group"], base_name(e["artifact"])) == (g, b)][0]
        if ver_tuple(v) < ver_tuple(_old["version"]):
            continue
        manifest = [e for e in manifest
                    if (e["group"], base_name(e["artifact"])) != (g, b)]
        _have.discard((g, b))
    got = download(g, a, v)
    if got:
        fn, kind = got
        manifest.append({"group": g, "artifact": a, "version": v, "type": kind,
                         "file": fn})
        print(f"  {g}:{a}:{v} ({kind})")
    else:
        print(f"  [MISSING] {g}:{a}:{v}")
json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w"), indent=1)
print("manifest:", os.path.join(OUT, "manifest.json"), "entries:", len(manifest))

# Extra: Shizuku client API (Maven Central, NOT Google Maven). Kept from
# resolve-aars.py: the api AAR alone is not enough (Shizuku.class references
# moe.shizuku.server.* at runtime; ShizukuProvider is a separate artifact).
_MC = "https://repo1.maven.org/maven2/dev/rikka/shizuku"
for _a in ("api", "aidl", "shared", "provider"):
    _jar = os.path.join(OUT, f"shizuku-{_a}-13.1.0.jar")
    if not (os.path.exists(_jar) and os.path.getsize(_jar) > 0):
        _aar = os.path.join(OUT, f"shizuku-{_a}-13.1.0.aar")
        if curl(f"{_MC}/{_a}/13.1.0/{_a}-13.1.0.aar", _aar):
            try:
                _z = zipfile.ZipFile(_aar)
                open(_jar, "wb").write(_z.read("classes.jar"))
                print(f"  shizuku:{_a}:13.1.0 (jar, from aar)")
            except Exception as _e:
                print(f"  [MISSING] shizuku:{_a}: {_e}")
                continue
        else:
            print(f"  [MISSING] shizuku:{_a}:13.1.0")
            continue
    _man = json.load(open(os.path.join(OUT, "manifest.json")))
    if ("dev.rikka.shizuku", _a) not in {(e["group"], e["artifact"]) for e in _man}:
        _man.append({"group": "dev.rikka.shizuku", "artifact": _a, "version": "13.1.0",
                     "type": "jar", "file": os.path.basename(_jar)})
        json.dump(_man, open(os.path.join(OUT, "manifest.json"), "w"), indent=1)
