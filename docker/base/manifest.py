#!/usr/bin/env python3
"""
Victor base-image dependency manifest tool.

Subcommands:
  scan <libdir>                          Emit {deps, depsHash} for jars in <libdir>.
  diff <baseline.json> <libdir>          Compare committed baseline vs current libdir.
                                         Emit {changed, baselineVersion, reason}.
  make <libdir> <baseVersion>            Emit full base manifest for embedding/committing.

A jar's identity = its filename (artifactId-version.jar). depsHash aggregates
filename + content sha256 so a version bump, an added/removed jar, or a same-name
content change all flip the hash and trigger a base rebuild.
"""
import hashlib
import json
import os
import sys
from datetime import datetime, timezone


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def scan_dir(libdir):
    jars = sorted(j for j in os.listdir(libdir) if j.endswith(".jar"))
    parts = []
    for name in jars:
        parts.append("{}|{}".format(name, sha256_file(os.path.join(libdir, name))))
    joined = "\n".join(parts)
    deps_hash = hashlib.sha256(joined.encode("utf-8")).hexdigest()
    return {"deps": jars, "depsHash": deps_hash}


def cmd_scan(args):
    libdir = args[0]
    if not os.path.isdir(libdir):
        sys.exit("scan: libdir not found: " + libdir)
    print(json.dumps(scan_dir(libdir), indent=2, ensure_ascii=False))


def cmd_diff(args):
    baseline_path, libdir = args[0], args[1]
    with open(baseline_path, "r", encoding="utf-8") as f:
        baseline = json.load(f)
    base_ver = baseline.get("baseVersion", "")
    if not base_ver:
        print(json.dumps({
            "changed": True,
            "baselineVersion": "",
            "reason": "baseline baseVersion is empty; no base image published yet",
        }, indent=2, ensure_ascii=False))
        return
    if not os.path.isdir(libdir):
        sys.exit("diff: libdir not found: " + libdir)
    current = scan_dir(libdir)
    b_deps, b_hash = baseline.get("deps", []), baseline.get("depsHash", "")
    c_deps, c_hash = current["deps"], current["depsHash"]
    if b_deps == c_deps and b_hash == c_hash:
        reason = "no change"
    else:
        only_old = [d for d in b_deps if d not in c_deps]
        only_new = [d for d in c_deps if d not in b_deps]
        reason = "deps changed"
        if only_old:
            reason += "; removed: " + ",".join(only_old[:10])
        if only_new:
            reason += "; added: " + ",".join(only_new[:10])
        if b_deps == c_deps:
            reason = "same names but content hash changed (patch-level drift)"
    print(json.dumps({
        "changed": not (b_deps == c_deps and b_hash == c_hash),
        "baselineVersion": base_ver,
        "reason": reason,
    }, indent=2, ensure_ascii=False))


def cmd_make(args):
    libdir, base_version = args[0], args[1]
    if not os.path.isdir(libdir):
        sys.exit("make: libdir not found: " + libdir)
    scanned = scan_dir(libdir)
    manifest = {
        "baseVersion": base_version,
        "deps": scanned["deps"],
        "depsCount": len(scanned["deps"]),
        "depsHash": scanned["depsHash"],
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    print(json.dumps(manifest, indent=2, ensure_ascii=False))


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: manifest.py {scan|diff|make} ...")
    cmd = sys.argv[1]
    args = sys.argv[2:]
    if cmd == "scan":
        cmd_scan(args)
    elif cmd == "diff":
        cmd_diff(args)
    elif cmd == "make":
        cmd_make(args)
    else:
        sys.exit("unknown command: " + cmd)


if __name__ == "__main__":
    main()
