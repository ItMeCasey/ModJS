# VS Code extension: development and publishing

The extension lives in `vscode-modjs/`. Its version is its own `YYYY.M.N` and does not have to equal the game version; the `.vsix` is named after it.

## Build and test

```
cd vscode-modjs
npm ci
npm test
npm run package
```

`npm run package` writes `modjs-<version>.vsix`. `npx vsce ls` lists the files that go into it (only `package.json`, `README.md`, `CHANGELOG.md`, `LICENSE`, `icon.png`, `snippets/` and the compiled `out/src/`).

## Workflows

| Workflow | When | What |
|---|---|---|
| `build.yml`, job `extension` | push or pull request that changes a file under `vscode-modjs/` | `npm ci`, `npm test`, `npm run package`; builds no game module |
| `release.yml`, job `vsix` | every tag release, after the `publish` job | calls `vsix-release.yml` for the tag; the extension version need not equal the tag, and only a failed build fails the job |
| `vsix-release.yml` | manual (input `tag`), or called by `release.yml` | checks out the tag, runs `npm ci`, `npm test`, `npm run package`, then `gh release upload <tag> modjs-<version>.vsix --clobber`; builds no game module, uploads nothing to CurseForge |
| `publish-extension.yml` | manual, input `publish` on | `vsce publish` when `VSCE_PAT` exists, `ovsx publish` when `OVSX_PAT` exists |

Paths under `vscode-modjs/` select no game build in `.github/scripts/plan_matrix.py`.

To attach the `.vsix` to a release that already exists (for example `v2026.11.38`), run the workflow `vsix-release` from the Actions tab with the tag as input. The release must exist; a `.vsix` of the same name already on it is replaced.

## Before the first publish (done by the repository owner)

1. Decide the publisher id. `package.json` has the placeholder `itmecasey`; it must equal the publisher id created in step 2, or the manifest is changed to match.
2. Visual Studio Marketplace: create a publisher at https://marketplace.visualstudio.com/manage with an Azure DevOps organization, then create a Personal Access Token with the scope Marketplace > Manage, for all accessible organizations.
3. Open VSX: sign in at https://open-vsx.org with GitHub, sign the Eclipse publisher agreement, create the namespace (`npx ovsx create-namespace <id> -p <token>`) and create an access token.
4. Add the repository secrets `VSCE_PAT` and `OVSX_PAT` (repository settings, Secrets and variables, Actions).
5. Run the workflow `publish-extension` manually with `publish` turned on.

Neither store token is stored in this repository, and the workflows never print them.
