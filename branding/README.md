# ModJS branding

Logo, banners and store listing copy for ModJS.

- `icon/`: the logo, 32x32 pixel art scaled up without smoothing. `icon_<size>.png` for every size, `icon.svg` for vector use, `modjs_logo.png` for the jar.
- `banners/`: description banner, gallery cards, social preview and section headers.
- `LISTING.md`: CurseForge and Modrinth fields, summaries and a setup checklist.
- `source/`: the scripts that draw all of it. Everything is original, including the pixel font; no Minecraft textures are used.

Regenerate from the repository root:

```
python branding/source/make_icon.py
python branding/source/render_banners.py
```

The second script needs Python, Pillow and Google Chrome.

## Palette

Shared with the other ItMeCasey Studios mods.

| Name | Hex | Use |
|---|---|---|
| Night | `#0f0e1a` to `#1a1830` | Backgrounds |
| Edge | `#2e2a46` | Borders, tile edge |
| Gold | `#f2b33d` (light `#ffe28c`, dark `#b27420`) | Wordmark, block, highlights |
| Code blue | `#7aa8ff` | Braces, keywords |
| Code green | `#7ed957` | Strings |
| Text | `#f4f2ff`, muted `#a9a4c8` | Copy |
