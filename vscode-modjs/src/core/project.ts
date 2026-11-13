import * as fs from 'fs';
import * as path from 'path';
import { DumpIndex } from './dump';

/** The default project file ModJS writes; it carries the marker the extension looks for. */
export const DEFAULT_JSCONFIG = `{
  "modjs": { "generated": true },
  "compilerOptions": {
    "target": "ES2020",
    "lib": ["ES2020"],
    "checkJs": true,
    "noEmit": true
  },
  "include": ["probe/**/*.d.ts", "startup_scripts/**/*.js", "server_scripts/**/*.js", "client_scripts/**/*.js"]
}
`;

const SCRIPT_FOLDERS = ['startup_scripts', 'server_scripts', 'client_scripts'];

function isModjsFolder(dir: string): boolean {
  const config = path.join(dir, 'jsconfig.json');
  try {
    if (fs.existsSync(config) && /"modjs"/.test(fs.readFileSync(config, 'utf8'))) {
      return true;
    }
  } catch {
    // An unreadable file is not a marker.
  }
  if (fs.existsSync(path.join(dir, 'probe', 'data', 'meta.json'))) {
    return true;
  }
  return SCRIPT_FOLDERS.some((name) => fs.existsSync(path.join(dir, name))) && fs.existsSync(path.join(dir, 'probe'));
}

/** The `modjs` folder a workspace folder is or contains, or undefined. */
export function findModjsRoot(workspaceFolder: string): string | undefined {
  if (path.basename(workspaceFolder).toLowerCase() === 'modjs' || isModjsFolder(workspaceFolder)) {
    if (isModjsFolder(workspaceFolder) || fs.existsSync(path.join(workspaceFolder, 'probe'))) {
      return workspaceFolder;
    }
  }
  const child = path.join(workspaceFolder, 'modjs');
  if (fs.existsSync(child) && fs.statSync(child).isDirectory()) {
    return child;
  }
  return undefined;
}

/** Whether a jsconfig's text lists the probe folder, or has no include list (so everything is included). */
export function includesProbe(jsconfigText: string): boolean {
  const include = /"include"\s*:\s*\[([\s\S]*?)\]/.exec(jsconfigText);
  if (!include) {
    return true;
  }
  return /probe\//.test(include[1]);
}

/** Adds the probe declaration files to the include list; returns undefined when the text has no include list to extend. */
export function addProbeInclude(jsconfigText: string): string | undefined {
  const include = /("include"\s*:\s*\[)([\s\S]*?)(\])/.exec(jsconfigText);
  if (!include) {
    return undefined;
  }
  const body = include[2];
  const entry = '"probe/**/*.d.ts"';
  const hasItems = body.trim().length > 0;
  const replaced = include[1] + (hasItems ? `${entry}, ${body.replace(/^\s+/, '')}` : entry) + include[3];
  return jsconfigText.slice(0, include.index) + replaced + jsconfigText.slice(include.index + include[0].length);
}

export function statusText(index: DumpIndex): string {
  if (!index.meta) {
    return 'ModJS: no dump';
  }
  const mods = index.meta.mods.length;
  return `MC ${index.meta.minecraft} · ${index.meta.loader} · ${mods} ${mods === 1 ? 'mod' : 'mods'}`;
}

export function statusTooltip(index: DumpIndex): string {
  if (!index.meta) {
    return 'No dump found in probe/data.';
  }
  const lines = [`Dump of ${index.meta.generatedAt}`, `ModJS ${index.meta.modjs}`, `Side: ${index.meta.side}`];
  if (index.meta.warnings.length) {
    lines.push(`Warnings: ${index.meta.warnings.length}`);
  }
  return lines.join('\n');
}
