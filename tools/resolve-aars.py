#!/usr/bin/env python3
"""Resolve AndroidX/Material AARs from Google Maven (dl.google.com) via curl.
Java TCP is blocked in this sandbox, but curl/Python TCP works.
Writes downloads to OUT dir, and a manifest JSON listing artifact -> file/type/package.
"""
import os, sys, json, subprocess, xml.etree.ElementTree as ET

BASE = "https://dl.google.com/dl/android/maven2"
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mvn/aars"
os.makedirs(OUT, exist_ok=True)

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def curl(url, dest):
    r = subprocess.run(["curl", "-sL", "-o", dest, "-w", "%{http_code}", url],
                       capture_output=True, text=True)
    code = r.stdout.strip()
    if code == "200" and os.path.getsize(dest) > 0:
        return True
    if os.path.exists(dest):
        os.remove(dest)
    return False

def fetch_pom(group, artifact, version):
    dest = os.path.join(OUT, f"{group}.{artifact}-{version}.pom")
    if os.path.exists(dest):
        return dest
    url = f"{BASE}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    return dest if curl(url, dest) else None

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
    parts = []
    for x in v.replace("-", ".").split("."):
        try:
            parts.append(int(x))
        except ValueError:
            parts.append(0)
    return tuple(parts)

resolved = {}   # (group, artifact) -> version (highest)
queue = []

def add(group, artifact, version):
    if not version or version.startswith("["):
        version = version.strip("[]").split(",")[0]
    key = (group, artifact)
    if key in resolved:
        if ver_tuple(version) > ver_tuple(resolved[key]):
            resolved[key] = version
            queue.append((group, artifact, version))
        return
    resolved[key] = version
    queue.append((group, artifact, version))

ROOTS = [
    ("com.google.android.material", "material", "1.13.0"),
    ("androidx.appcompat", "appcompat", "1.7.0"),
    ("androidx.recyclerview", "recyclerview", "1.3.2"),
    ("androidx.core", "core-ktx", "1.13.1"),
    ("androidx.drawerlayout", "drawerlayout", "1.2.0"),
    ("androidx.coordinatorlayout", "coordinatorlayout", "1.2.0"),
]
SKIP = {("org.jetbrains.kotlin", "kotlin-stdlib"), ("org.jetbrains.kotlin", "kotlin-bom"),
        ("org.jetbrains", "annotations")}
for g, a, v in ROOTS:
    add(g, a, v)

seen_pom = set()
while queue:
    group, artifact, version = queue.pop(0)
    if (group, artifact) in SKIP:
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
    # parent POM for properties
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
                    proot = ET.parse(ppom).getroot()
                    parent_props = parse_props(proot)
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
        if (g, a) in SKIP:
            continue
        add(g, a, v)

print(f"resolved {len(resolved)} artifacts")
manifest = []
for (g, a), v in sorted(resolved.items()):
    base = f"{BASE}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}"
    aar = os.path.join(OUT, f"{a}-{v}.aar")
    jar = os.path.join(OUT, f"{a}-{v}.jar")
    kind = None
    if os.path.exists(aar) and os.path.getsize(aar) > 0:
        kind = "aar"
    elif curl(base + ".aar", aar):
        kind = "aar"
    elif curl(base + ".jar", jar):
        kind = "jar"
    else:
        print(f"  [MISSING] {g}:{a}:{v}")
        continue
    # package name from pom packaging? guess from aar manifest later; store group/artifact
    manifest.append({"group": g, "artifact": a, "version": v, "type": kind,
                     "file": aar if kind == "aar" else jar})
    print(f"  {g}:{a}:{v} ({kind})")
json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w"), indent=1)
print("manifest:", os.path.join(OUT, "manifest.json"), "entries:", len(manifest))

# Extra: Shizuku client API (Maven Central, NOT Google Maven). The api AAR
# alone is not enough: Shizuku.class references moe.shizuku.server.* (aidl)
# at runtime, and the ShizukuProvider lives in the separate provider artifact.
# All four AARs are pure code (no res/), so only their classes.jar is kept
# and registered as "jar" entries (a jar entry skips the R.java guard, which
# would otherwise fail for a res-less package).
import zipfile as _zf
_MC = "https://repo1.maven.org/maven2/dev/rikka/shizuku"
for _a in ("api", "aidl", "shared", "provider"):
    _jar = os.path.join(OUT, f"shizuku-{_a}-13.1.0.jar")
    if not (os.path.exists(_jar) and os.path.getsize(_jar) > 0):
        _aar = os.path.join(OUT, f"shizuku-{_a}-13.1.0.aar")
        if curl(f"{_MC}/{_a}/13.1.0/{_a}-13.1.0.aar", _aar):
            try:
                _z = _zf.ZipFile(_aar)
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

# Extra: LiquidGlass (JitPack, NOT Google Maven). QWEA0/Liquid-Glass-Android
# v2.0.11 — iOS-26-style liquid glass for the View system (real SDF refraction
# on API 33+, classic CPU blur pipeline on API 24+). Replaces the earlier
# Dimezis/BlurView route (removed 2026-11-20). The AAR has res/ (attrs) and
# arm-only native .so (arm64-v8a/armeabi-v7a); its POM wants kotlin-stdlib
# 2.0.21 + core-ktx 1.13.1 (core-ktx already resolved as a transitive dep;
# stdlib stays the kotlinc-bundled 1.9.22 — see MainActivity NATIVE_GLASS_OK).
# JitPack coordinate is com.github.QWEA0:liquidglass (NOT ...:Liquid-Glass-Android).
_JP = "https://jitpack.io/com/github/QWEA0/liquidglass/v2.0.11"
_lg_aar = os.path.join(OUT, "liquidglass-v2.0.11.aar")
if not (os.path.exists(_lg_aar) and os.path.getsize(_lg_aar) > 0):
    if curl(f"{_JP}/liquidglass-v2.0.11.aar", _lg_aar):
        print("  com.github.QWEA0:liquidglass:v2.0.11 (aar)")
    else:
        print("  [MISSING] liquidglass:v2.0.11")
_man = json.load(open(os.path.join(OUT, "manifest.json")))
if ("com.github.QWEA0", "liquidglass") not in {(e["group"], e["artifact"]) for e in _man}:
    _man.append({"group": "com.github.QWEA0", "artifact": "liquidglass", "version": "v2.0.11",
                 "type": "aar", "file": os.path.basename(_lg_aar)})
    json.dump(_man, open(os.path.join(OUT, "manifest.json"), "w"), indent=1)
