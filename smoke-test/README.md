# Smoke test scripts

Copy `server_scripts` and `startup_scripts` into `forge-<version>/run/modjs/`, then run `./gradlew runGameTestServer`
inside that module. The log should show the startup script, `ModJS recipes: ... 1 removed, 1 rewritten`, the planks tag
check, and `SELFTEST PASS` lines ending in `SELFTEST DONE failures=0`.

For a full local module check, use `python scripts/verify_matrix.py <module> ...` from the repository root.
It preserves existing scripts and optional mod jars, installs these canonical smoke scripts, runs the build
and headless self-test, retains concise evidence in `docs/testing`, and restores the original files.
Existing game-test worlds are set to Peaceful with spawning disabled before launch; the loaded hook
also prepares newly generated test worlds. Full build logs stay under ignored `release/`.
