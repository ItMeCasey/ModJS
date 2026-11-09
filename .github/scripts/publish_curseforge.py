#!/usr/bin/env python3
"""Uploads the built ModJS jars to CurseForge.

Usage:
  publish_curseforge.py --artifacts DIR --version 2026.11.23 --changelog-file notes.md [--release-type beta] [--dry-run]
  publish_curseforge.py --list-versions

DIR holds one folder per build, named like the CI artifacts (jar-forge-1.21.1, jar-neoforge-26.1.2, ...), each with the
jars Gradle produced. The upload token is read from the CURSEFORGE_TOKEN environment variable and is never printed.
Only the Python standard library is used.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

PROJECT_ID = 1783874
API = "https://minecraft.curseforge.com/api"

# module -> (Minecraft version, loader name on CurseForge, Java version name on CurseForge)
BUILDS = {
    "forge-26.3": ("26.3", "Forge", "Java 25"),
    "neoforge-26.3": ("26.3", "NeoForge", "Java 25"),
    "forge-26.2": ("26.2", "Forge", "Java 25"),
    "neoforge-26.2": ("26.2", "NeoForge", "Java 25"),
    "forge-26.1.2": ("26.1.2", "Forge", "Java 25"),
    "neoforge-26.1.2": ("26.1.2", "NeoForge", "Java 25"),
    "forge-1.21.1": ("1.21.1", "Forge", "Java 21"),
    "neoforge-1.21.1": ("1.21.1", "NeoForge", "Java 21"),
    "forge-1.20.1": ("1.20.1", "Forge", "Java 17"),
    "neoforge-1.20.1": ("1.20.1", "NeoForge", "Java 17"),
}
RELEASE_TYPES = ("alpha", "beta", "release")
ROOT = Path(__file__).resolve().parents[2]


def module_version(module):
    """The mod_version in the module's gradle.properties, or None when the module folder is missing."""
    props = ROOT / module / "gradle.properties"
    if not props.is_file():
        return None
    for line in props.read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    return None


def selected(version):
    """The builds to upload: only those whose own mod_version is this version; the rest are skipped and logged."""
    chosen = []
    for module in BUILDS:
        own = module_version(module)
        if own == version:
            chosen.append(module)
        else:
            print(f"skipping {module}: mod_version {own} is not {version}")
    if not chosen:
        sys.exit(f"No build has mod_version {version}")
    return chosen


def token():
    value = os.environ.get("CURSEFORGE_TOKEN", "")
    if not value:
        sys.exit("CURSEFORGE_TOKEN is not set")
    return value


def request(url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, headers={"X-Api-Token": token(), **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")[:500]
        sys.exit(f"CurseForge answered {error.code} for {url}: {body}")


def game_versions():
    return request(f"{API}/game/versions")


def resolve(versions, name):
    matches = [v for v in versions if v["name"] == name]
    if not matches:
        close = sorted({v["name"] for v in versions if name.split(".")[0] in v["name"]})[:15]
        sys.exit(f"CurseForge has no game version named '{name}'. Similar names: {close}")
    return matches[0]["id"]


def pick_jar(folder, module, version):
    # NeoForge 1.20.1 still runs on Forge's own (SRG) names, and only the Forge build is reobfuscated to them. The Forge 1.20.1
    # jar loads on NeoForge 47.1.106 (checked in a real install), so that one file is uploaded for both loaders.
    if module == "neoforge-1.20.1":
        folder = folder.parent / "jar-forge-1.20.1"
    jars = [p for p in sorted(folder.glob("*.jar")) if version in p.name and not any(p.name.endswith(s) for s in ("-sources.jar", "-slim.jar", "-javadoc.jar", "-all.jar"))]
    if len(jars) != 1:
        sys.exit(f"Expected exactly one jar to upload for {module} in {folder}, found {[p.name for p in jars]}")
    return jars[0]


def clean_name(module, version):
    """The file name people download: modjs-<loader>-mc<minecraft>-<version>.jar."""
    minecraft, loader, _ = BUILDS[module]
    return f"modjs-{loader.lower()}-mc{minecraft}-{version}.jar"


def multipart(fields, file_field, path, name):
    boundary = uuid.uuid4().hex
    lines = []
    for field, value in fields.items():
        lines.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"\r\n"
                      f"Content-Type: application/json\r\n\r\n{value}\r\n").encode("utf-8"))
    lines.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; filename=\"{name}\"\r\n"
                  "Content-Type: application/java-archive\r\n\r\n").encode("utf-8"))
    lines.append(path.read_bytes())
    lines.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    return b"".join(lines), f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", type=Path)
    parser.add_argument("--version")
    parser.add_argument("--changelog-file", type=Path)
    parser.add_argument("--release-type", default="beta", choices=RELEASE_TYPES)
    parser.add_argument("--dry-run", action="store_true", help="resolve everything and show the plan without uploading")
    parser.add_argument("--collect", type=Path, help="copy the jars into this folder under their download names and stop (no token needed)")
    parser.add_argument("--list-versions", action="store_true", help="print the game versions CurseForge knows (read only)")
    args = parser.parse_args()

    if args.list_versions:
        for v in game_versions():
            print(v["id"], v["name"])
        return
    if args.collect:
        if not (args.artifacts and args.version):
            parser.error("--collect needs --artifacts and --version")
        args.collect.mkdir(parents=True, exist_ok=True)
        for module in selected(args.version):
            jar = pick_jar(args.artifacts / f"jar-{module}", module, args.version)
            target = args.collect / clean_name(module, args.version)
            target.write_bytes(jar.read_bytes())
            print(f"{module}: {jar.name} -> {target.name}")
        return
    if not (args.artifacts and args.version and args.changelog_file):
        parser.error("--artifacts, --version and --changelog-file are required")

    versions = game_versions()
    changelog = args.changelog_file.read_text(encoding="utf-8")
    plan = []
    for module in selected(args.version):
        minecraft, loader, java = BUILDS[module]
        folder = args.artifacts / f"jar-{module}"
        if not folder.is_dir():
            sys.exit(f"Missing build folder {folder}")
        jar = pick_jar(folder, module, args.version)
        # CurseForge also wants an environment (Client and Server): ModJS runs on both.
        ids = [resolve(versions, minecraft), resolve(versions, loader), resolve(versions, "Client"), resolve(versions, "Server")]
        try:
            ids.append(resolve(versions, java))
        except SystemExit:
            print(f"note: CurseForge has no '{java}', uploading {module} without a Java version")
        plan.append((module, jar, ids, f"ModJS {args.version} ({loader} {minecraft})", clean_name(module, args.version)))

    for module, jar, ids, title, name in plan:
        print(f"{title}: {name} ({jar.stat().st_size // 1024} KiB) game version ids {ids}, {args.release_type}")
    if args.dry_run:
        print("dry run: nothing uploaded")
        return

    for module, jar, ids, title, name in plan:
        metadata = json.dumps({
            "changelog": changelog,
            "changelogType": "markdown",
            "displayName": title,
            "gameVersions": ids,
            "releaseType": args.release_type,
        })
        body, content_type = multipart({"metadata": metadata}, "file", jar, name)
        result = request(f"{API}/projects/{PROJECT_ID}/upload-file", data=body, headers={"Content-Type": content_type})
        print(f"uploaded {name} as file id {result.get('id')}")


if __name__ == "__main__":
    main()
