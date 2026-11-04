# Releasing ModJS

A release is a tag. Pushing `v<version>` makes GitHub build all six jars, run each build's self test, publish a GitHub
release with the jars and upload them to CurseForge (project 1783874).

## One-time setup

Add the CurseForge upload token as a repository secret named `CURSEFORGE_TOKEN`. From PowerShell, with the token in your
Windows user environment (it is read from there and never printed):

```powershell
[Environment]::GetEnvironmentVariable('CURSEFORGE_TOKEN','User') | gh secret set CURSEFORGE_TOKEN --repo ItMeCasey/ModJS
```

## Each release

1. Set `mod_version` to the new version (`YYYY.M.increment`) in all six `gradle.properties` files.
2. Commit and push to `main`, and wait for the `build` workflow to pass.
3. Tag and push:

   ```
   git tag v2026.11.24
   git push origin v2026.11.24
   ```

The `release` workflow stops if the tag and `mod_version` disagree, or if the modules disagree with each other.
Releases are uploaded to CurseForge as **beta**. Run the workflow by hand (Actions, release, Run workflow) to pick
`alpha` or `release`; a manual run only uploads when "Upload to CurseForge" is ticked.

## What is uploaded

| File | Minecraft | Loader |
|---|---|---|
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
