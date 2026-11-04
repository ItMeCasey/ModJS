# ModJS listing kit: CurseForge and Modrinth

Everything you need to fill in the two project forms. Files are in `branding/icon/` and `branding/banners/`.

## Project fields

| Field | CurseForge | Modrinth |
|---|---|---|
| Project ID | 1783874 | (not created yet) |
| Name | ModJS | ModJS |
| Slug / URL | `modjs` | `modjs` |
| Icon / logo | `icon/icon_512.png` (square, at least 400x400) | `icon/icon_512.png` (square, under 256 KiB) |
| Class | Mods | Mod |
| Main category | Utility & QoL | Utility |
| More categories | API and Library | Library |
| Loaders | Forge, NeoForge | Forge, NeoForge |
| Game versions | Set on each uploaded file (1.20.1, 1.21.1, 26.1.2) | Set on each uploaded file |
| Environment | Client and server | Client: required, Server: required |
| License | see the repository | see the repository |
| Source | https://github.com/ItMeCasey/ModJS | same |
| Issues | https://github.com/ItMeCasey/ModJS/issues | same |
| Wiki | https://itmecasey.com/wiki/modjs/ | same |

### One-line description

> Script Minecraft in JavaScript. Add items, blocks, recipes and whole dimensions, react to gameplay and rebuild the main menu, all from plain text files.

### Summary

Modrinth (256 characters max):

> KubeJS-style scripting for Forge and NeoForge. Write JavaScript to add items, blocks, recipes and dimensions, react to gameplay, unify materials across mods and rebuild the title screen. No Java needed.

CurseForge (short, shows on cards):

> Script Minecraft in JavaScript. Add items, blocks, recipes and dimensions, react to gameplay and rebuild the main menu with plain text files.

### Gallery

| File | Use |
|---|---|
| `banners/features.png` (1920x1080) | Featured gallery image on Modrinth, first image on CurseForge |
| `banners/scripts.png` (1920x1080) | The three script folders, second gallery image |
| `banners/social.png` (1280x640) | GitHub social preview (repo Settings > Social preview), third gallery image |
| `banners/banner.png` (800x150, saved at 2x) | Top of both descriptions |
| `banners/h_*.png` (800x46, saved at 2x) | Section headers inside the descriptions |

The code in these images is illustration written for the branding, not a screenshot. Add real in-game screenshots to the gallery when you have them, such as a custom title screen or a scripted item in the creative tab.

### Image links in the descriptions

The description loads images from this repository (`raw.githubusercontent.com/ItMeCasey/ModJS/main/branding/...`), so they show once `branding/` is pushed to the public repository.

## Description

The CurseForge description is [`curseforge/DESCRIPTION.md`](../curseforge/DESCRIPTION.md). Paste it with the description type set to **Markdown**. It also works on Modrinth.

## Checklist

1. Push `branding/` and `curseforge/` to GitHub so the description images load.
2. Create the CurseForge project with `icon_512.png` as the logo, then upload the jars (one per Minecraft version and loader). CurseForge reviews new projects, so it needs a first file.
3. Create the Modrinth project the same way. Upload `features.png` as the featured gallery image.
4. Set `banners/social.png` as the GitHub social preview.
5. Optional: add `icon/modjs_logo.png` to the jars as the mod list logo (`logoFile` in the mod metadata).
