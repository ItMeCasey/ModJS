#!/usr/bin/env python3
"""Makes modjs/names.txt.gz for the 1.20.1 builds from the mappings Gradle already built.

A real Minecraft 1.20.1 game only has Forge's own member names (m_82127_, f_42415_). Scripts use the official names (Commands.literal,
Items.DIAMOND), so the 1.20.1 jars ship this table and the script engine translates (see script/names/NameMap.java).

Run from the repository root after `cd forge-1.20.1 && ./gradlew createSrgToMcp`:
    python scripts/gen_name_map.py
It reads forge-1.20.1/build/createSrgToMcp/output.srg and writes the same resource into forge-1.20.1 and neoforge-1.20.1.
"""
import gzip
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRG = ROOT / "forge-1.20.1" / "build" / "createSrgToMcp" / "output.srg"
TARGETS = [ROOT / "forge-1.20.1" / "src" / "main" / "resources" / "modjs" / "names.txt.gz",
           ROOT / "neoforge-1.20.1" / "src" / "main" / "resources" / "modjs" / "names.txt.gz"]
REAL = re.compile(r"^[mf]_\d+_$")


def main():
    if not SRG.exists():
        sys.exit(f"missing {SRG}: run ./gradlew createSrgToMcp in forge-1.20.1 first")
    classes = {}
    for line in SRG.read_text(encoding="utf-8").splitlines():
        parts = line.split(" ")
        if parts[0] == "MD:" and len(parts) >= 5:
            kind, srg_ref, official_ref = "m", parts[1], parts[3]
        elif parts[0] == "FD:" and len(parts) >= 3:
            kind, srg_ref, official_ref = "f", parts[1], parts[2]
        else:
            continue
        owner, _, srg = srg_ref.rpartition("/")
        official = official_ref.rpartition("/")[2]
        if not REAL.match(srg) or official.startswith("lambda$") or "$" in official or official.startswith("access"):
            continue
        classes.setdefault(owner.replace("/", "."), []).append(f"{kind}\t{official}\t{srg}")
    out = []
    for owner in sorted(classes):
        out.append("#" + owner)
        out.extend(sorted(set(classes[owner])))
    data = ("\n".join(out) + "\n").encode("utf-8")
    for target in TARGETS:
        target.parent.mkdir(parents=True, exist_ok=True)
        with open(target, "wb") as handle, gzip.GzipFile(filename="", mode="wb", fileobj=handle, mtime=0) as gz:
            gz.write(data)
        print(f"{target.relative_to(ROOT)}: {len(classes)} classes, {len(data) // 1024} KiB, {target.stat().st_size // 1024} KiB gzipped")


if __name__ == "__main__":
    main()
