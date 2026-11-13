#!/usr/bin/env python3
"""Pick the builds a workflow run needs, so unchanged versions are never built.

  plan_matrix.py build   [--before SHA] [--sha SHA] [--files F ...]   builds affected by the changed files
  plan_matrix.py release --tag vYYYY.M.N                              builds whose own mod_version equals the tag

Writes `matrix=<json>` and `any=true|false` to $GITHUB_OUTPUT (and prints them). In build mode it also writes
`extension=true|false`: true when a changed file is under vscode-modjs/ (the VS Code extension, which no game build uses).

How ModJS shares code (checked against every module's build.gradle):
  <loader>-<mc>/     one module, its own folder
  common/            compiled into every module (java, resources, unit tests)
  libs/smwti-api-<mc>.jar   compileOnly for the two modules of that Minecraft version
  smoke-test/        copied into every module's game test server run (build workflow self test)
  examples/          read by the unit tests of every module (ExampleScriptsSyntaxTest, RegistryExampleTest)
  forge-1.20.1/src/main/java   also checked against neoforge-1.20.1/src/main/java (checkLegacySourceParity, part of `check`)
Docs, scripts (tours), branding, curseforge text, markdown and CI files are not compiled into any jar and select nothing.
An unknown path, or a diff that cannot be computed, selects everything.
"""
import argparse
import json
import os
import re
import subprocess
import sys

# (module, java); the folder of a module is its name
ALL = [
    ("forge-26.3", 25),
    ("neoforge-26.3", 25),
    ("forge-26.2", 25),
    ("neoforge-26.2", 25),
    ("forge-26.1.2", 25),
    ("forge-1.21.1", 21),
    ("forge-1.20.1", 17),
    ("neoforge-1.20.1", 17),
    ("neoforge-1.21.1", 21),
    ("neoforge-26.1.2", 25),
]
NAMES = [m for m, _ in ALL]
ZERO = "0" * 40

# Folders that no jar is built from.
NO_BUILD_PREFIXES = (
    "docs/", "branding/", "curseforge/", "scripts/", "release/", "logs/", "dist/", "vscode-modjs/",
    ".github/ISSUE_TEMPLATE/", ".github/workflows/", ".github/scripts/",
)
NO_BUILD_ROOT_FILES = {".gitignore", "LICENSE", "README.md", "CHANGELOG.md", "PROJECT_STATUS.md"}


def changed_files(before, sha):
    """The files changed between two commits, or None when git cannot tell (then everything is built)."""
    if not before or before == ZERO:
        # first push of a branch or a manual run: compare with the parent commit
        before = sha + "^"
    out = subprocess.run(["git", "diff", "--name-only", before, sha], capture_output=True, text=True)
    if out.returncode != 0:
        return None
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def affected(path):
    """The module names one changed path affects (a set). Unknown paths affect every module."""
    path = path.replace("\\", "/")
    if path.startswith("./"):
        path = path[2:]
    if path in NO_BUILD_ROOT_FILES:
        return set()
    if path.startswith(NO_BUILD_PREFIXES):
        return set()
    if "/" not in path and path.endswith(".md"):
        return set()
    top = path.split("/", 1)[0]
    if top in NAMES:
        picked = {top}
        if path.startswith("forge-1.20.1/src/main/java/"):
            picked.add("neoforge-1.20.1")  # the parity check compares both 1.20.1 source trees
        return picked
    if top in ("common", "smoke-test", "examples"):
        return set(NAMES)
    if top == "libs":
        m = re.fullmatch(r"libs/smwti-api-(.+)\.jar", path)
        if m:
            return {n for n in NAMES if n.endswith("-" + m.group(1))}
        return set(NAMES)
    return set(NAMES)


def plan_build(files):
    """The modules to build for a list of changed files (None means the list is unknown)."""
    if files is None:
        return list(ALL)
    picked = set()
    for f in files:
        picked |= affected(f)
    return [m for m in ALL if m[0] in picked]


def plan_extension(files):
    """True when the VS Code extension needs its own check (None means the list is unknown)."""
    if files is None:
        return True
    return any(f.replace("\\", "/").lstrip("./").startswith("vscode-modjs/") for f in files)


def module_version(module, root="."):
    try:
        with open(os.path.join(root, module, "gradle.properties"), encoding="utf-8") as fh:
            text = fh.read()
    except OSError:
        return None
    m = re.search(r"^mod_version=(\S+)", text, re.M)
    return m.group(1) if m else None


def version_key(v):
    return [int(x) for x in v.split(".")]


def plan_release(tag, root="."):
    """The modules whose own mod_version equals the tag version (the newest version found when the run is not a tag)."""
    version = (tag or "").lstrip("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        found = {module_version(m, root) for m, _ in ALL} - {None}
        version = sorted(found, key=version_key)[-1] if found else ""
    picked = [m for m in ALL if module_version(m[0], root) == version]
    # NeoForge 1.20.1 is uploaded as the Forge 1.20.1 jar, so that jar must be built whenever NeoForge 1.20.1 is released.
    names = {m for m, _ in picked}
    if "neoforge-1.20.1" in names and "forge-1.20.1" not in names:
        picked = [m for m in ALL if m[0] in names | {"forge-1.20.1"}]
    return version, picked


def emit(picked, extension=None):
    matrix = {"include": [{"module": m, "java": j} for m, j in picked]}
    lines = [f"matrix={json.dumps(matrix)}", f"any={'true' if picked else 'false'}"]
    if extension is not None:
        lines.append(f"extension={'true' if extension else 'false'}")
    print("\n".join(lines))
    print("builds:", ", ".join(m for m, _ in picked) or "none")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["build", "release"])
    ap.add_argument("--before")
    ap.add_argument("--sha", default="HEAD")
    ap.add_argument("--tag")
    ap.add_argument("--files", nargs="*", help="changed files to use instead of asking git (for tests)")
    args = ap.parse_args(argv)

    if args.mode == "release":
        version, picked = plan_release(args.tag)
        if not picked:
            emit(picked)
            sys.exit(f"No build has mod_version {version}")
    else:
        files = args.files if args.files is not None else changed_files(args.before, args.sha)
        picked = plan_build(files)
        emit(picked, plan_extension(files))
        return
    emit(picked)


if __name__ == "__main__":
    main()
