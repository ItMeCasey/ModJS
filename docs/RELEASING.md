# Releasing ModJS

A release is a tag. Pushing `v<version>` makes GitHub build every module, run each build's self test, publish a GitHub
release and upload to CurseForge (project 1783874) only the builds that changed.

## One-time setup

Add the CurseForge upload token as a repository secret named `CURSEFORGE_TOKEN`. From PowerShell, with the token in your
Windows user environment (it is read from there and never printed):

```powershell
[Environment]::GetEnvironmentVariable('CURSEFORGE_TOKEN','User') | gh secret set CURSEFORGE_TOKEN --repo ItMeCasey/ModJS
```

## Each release

1. Set `mod_version` to the new version (`YYYY.M.increment`) in the `gradle.properties` of only the builds that changed. Leave the
   other builds at their old version: they are not uploaded again (duplicate uploads are denied on CurseForge).
2. Commit and push to `main`, and wait for the `build` workflow to pass.
3. Tag and push:

   ```
   git tag v2026.11.25
   git push origin v2026.11.25
   ```

The `release` workflow uploads a build only when its own `mod_version` equals the tag version, logs `skipping <module>` for the rest,
and fails only when no build matches the tag. A manual run uses the newest `mod_version`. Every module is still built and self-tested.
Releases are uploaded to CurseForge as **beta**. Run the workflow by hand (Actions, release, Run workflow) to pick
`alpha` or `release`; a manual run only uploads when "Upload to CurseForge" is ticked.

## What is uploaded

| File | Minecraft | Loader |
|---|---|---|
| `modjs-forge-mc26.3-<version>.jar` | 26.3 | Forge |
| `modjs-neoforge-mc26.3-<version>.jar` | 26.3 | NeoForge |
| `modjs-forge-mc26.2-<version>.jar` | 26.2 | Forge |
| `modjs-neoforge-mc26.2-<version>.jar` | 26.2 | NeoForge |
| `modjs-forge-mc26.1.2-<version>.jar` | 26.1.2 | Forge |
| `modjs-neoforge-mc26.1.2-<version>.jar` | 26.1.2 | NeoForge |
| `modjs-forge-mc1.21.1-<version>.jar` | 1.21.1 | Forge |
| `modjs-neoforge-mc1.21.1-<version>.jar` | 1.21.1 | NeoForge |
| `modjs-forge-mc1.20.1-<version>.jar` | 1.20.1 | Forge |
| `modjs-neoforge-mc1.20.1-<version>.jar` | 1.20.1 | NeoForge |

The NeoForge 1.20.1 file is the `-all` jar, which carries the script engine. The changelog is the commit subjects since
the previous tag.

## Checking the upload without publishing

`.github/scripts/publish_curseforge.py --dry-run` resolves every CurseForge game version id and prints what it would
upload, without uploading. It needs `CURSEFORGE_TOKEN` in the environment.
