# Smoke test scripts

Copy `server_scripts` and `startup_scripts` into `forge-<version>/run/modjs/`, then run `./gradlew runGameTestServer`
inside that module. The log should show the startup script, `ModJS recipes: ... 1 removed, 1 rewritten`, the planks tag
check, and `SELFTEST PASS` lines ending in `SELFTEST DONE failures=0`.
