#!/usr/bin/env python3
"""Resolve Media3 ExoPlayer (androidx.media3:media3-exoplayer 1.4.1) from
Google Maven and merge into the existing build-direct/aars/manifest.json.
Same approach as tools/resolve-aars.py (curl; Java TCP is blocked here).
Only the exoplayer artifact is a root: no media3-ui (custom Compose UI),
no session/cast. Transitive deps resolve the same way as resolve-aars.py.
SKIP covers things already in the manifest at higher versions via -jvm twins
(annotation, collection) or the stdlib.
"""
import os, sys, json, subprocess, xml.etree.ElementTree as ET

BASE = "https://dl.google.com/dl/android/maven2"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build-direct", "aars")
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

man_path = os.path.join(OUT, "manifest.json")
manifest = json.load(open(man_path)) if os.path.exists(man_path) else []
resolved = {(e["group"], e["artifact"]): e["version"] for e in manifest}
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

ROOTS = [("androidx.media3", "media3-exoplayer", "1.4.1")]
SKIP = {("org.jetbrains.kotlin", "kotlin-stdlib"), ("org.jetbrains.kotlin", "kotlin-bom"),
        ("org.jetbrains", "annotations"),
        ("androidx.annotation", "annotation"),
        ("androidx.collection", "collection")}
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

print(f"resolved {len(resolved)} artifacts (manifest had {len(manifest)})")
new_entries = []
for (g, a), v in sorted(resolved.items()):
    if any(e["group"] == g and e["artifact"] == a for e in manifest):
        continue
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
    new_entries.append({"group": g, "artifact": a, "version": v, "type": kind,
                        "file": aar if kind == "aar" else jar})
    print(f"  NEW {g}:{a}:{v} ({kind})")
manifest.extend(new_entries)
json.dump(manifest, open(man_path, "w"), indent=1)
print("manifest:", man_path, "entries:", len(manifest))
