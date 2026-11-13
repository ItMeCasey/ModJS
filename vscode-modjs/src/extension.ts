import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { stringAt, callContext } from './core/context';
import { DumpIndex } from './core/dump';
import { EventTable, parseEvents } from './core/events';
import { completionsAt, diagnose, hoverAt, identify } from './core/features';
import { addProbeInclude, DEFAULT_JSCONFIG, findModjsRoot, includesProbe, statusText, statusTooltip } from './core/project';
import { resolveTarget } from './core/resolve';

const SCHEME = 'modjs-dump';
const LANGUAGES = ['javascript', 'typescript'];

interface Project {
  root: string;
  probe: string;
  index: DumpIndex;
  events: EventTable;
}

const projects = new Map<string, Project>();
let statusBar: vscode.StatusBarItem;
let diagnostics: vscode.DiagnosticCollection;

function loadProject(root: string): Project {
  const probe = path.join(root, 'probe');
  const index = DumpIndex.load(probe);
  let events: EventTable = new Map();
  try {
    events = parseEvents(fs.readFileSync(path.join(probe, 'modjs.d.ts'), 'utf8'));
  } catch {
    // Without the declarations there is no event table, and event names are not checked.
  }
  return { root, probe, index, events };
}

function projectFor(file: string): Project | undefined {
  for (const project of projects.values()) {
    const relative = path.relative(project.root, file);
    if (!relative.startsWith('..') && !path.isAbsolute(relative)) {
      return project;
    }
  }
  return undefined;
}

function isScript(document: vscode.TextDocument): boolean {
  return LANGUAGES.includes(document.languageId) && document.uri.scheme === 'file' && projectFor(document.uri.fsPath) !== undefined;
}

function updateStatus(): void {
  const editor = vscode.window.activeTextEditor;
  const project = (editor && projectFor(editor.document.uri.fsPath)) ?? [...projects.values()][0];
  if (!project) {
    statusBar.hide();
    return;
  }
  statusBar.text = `$(package) ${statusText(project.index)}`;
  statusBar.tooltip = statusTooltip(project.index);
  statusBar.show();
}

function refreshDiagnostics(document: vscode.TextDocument): void {
  if (!isScript(document) || !vscode.workspace.getConfiguration('modjs').get<boolean>('diagnostics.enabled', true)) {
    diagnostics.delete(document.uri);
    return;
  }
  const project = projectFor(document.uri.fsPath)!;
  const text = document.getText();
  const items = diagnose(text, project.index, project.events).map((problem) => {
    const range = new vscode.Range(document.positionAt(problem.start), document.positionAt(problem.end));
    const item = new vscode.Diagnostic(range, problem.message, problem.severity === 'error' ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Warning);
    item.source = 'ModJS';
    return item;
  });
  diagnostics.set(document.uri, items);
}

function reloadAll(): void {
  for (const [root] of projects) {
    projects.set(root, loadProject(root));
  }
  updateStatus();
  vscode.workspace.textDocuments.forEach(refreshDiagnostics);
}

async function checkProjectFile(root: string): Promise<void> {
  const config = path.join(root, 'jsconfig.json');
  if (!fs.existsSync(config)) {
    fs.writeFileSync(config, DEFAULT_JSCONFIG, 'utf8');
    return;
  }
  const text = fs.readFileSync(config, 'utf8');
  if (includesProbe(text)) {
    return;
  }
  const fixed = addProbeInclude(text);
  if (fixed === undefined) {
    return;
  }
  const choice = await vscode.window.showInformationMessage('jsconfig.json does not include the probe folder.', 'Add it');
  if (choice === 'Add it') {
    fs.writeFileSync(config, fixed, 'utf8');
  }
}

function watch(context: vscode.ExtensionContext, project: Project): void {
  const pattern = new vscode.RelativePattern(project.probe, '**/*');
  const watcher = vscode.workspace.createFileSystemWatcher(pattern);
  let timer: NodeJS.Timeout | undefined;
  const changed = () => {
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(reloadAll, 400);
  };
  watcher.onDidChange(changed);
  watcher.onDidCreate(changed);
  watcher.onDidDelete(changed);
  context.subscriptions.push(watcher);
}

function discover(context: vscode.ExtensionContext): void {
  for (const folder of vscode.workspace.workspaceFolders ?? []) {
    const root = findModjsRoot(folder.uri.fsPath);
    if (root && !projects.has(root)) {
      projects.set(root, loadProject(root));
      watch(context, projects.get(root)!);
      void checkProjectFile(root);
    }
  }
}

export function activate(context: vscode.ExtensionContext): void {
  statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 50);
  statusBar.command = 'modjs.refreshDump';
  diagnostics = vscode.languages.createDiagnosticCollection('modjs');
  context.subscriptions.push(statusBar, diagnostics);

  discover(context);
  if (projects.size === 0) {
    return;
  }
  updateStatus();

  const selector = LANGUAGES.map((language) => ({ language, scheme: 'file' }));

  context.subscriptions.push(
    vscode.workspace.onDidChangeWorkspaceFolders(() => discover(context)),
    vscode.window.onDidChangeActiveTextEditor(updateStatus),
    vscode.workspace.onDidOpenTextDocument(refreshDiagnostics),
    vscode.workspace.onDidSaveTextDocument(refreshDiagnostics),
    vscode.workspace.onDidCloseTextDocument((document) => diagnostics.delete(document.uri)),
    vscode.workspace.onDidChangeTextDocument((event) => {
      const document = event.document;
      setTimeout(() => refreshDiagnostics(document), 300);
    }),

    vscode.languages.registerCompletionItemProvider(selector, {
      provideCompletionItems(document, position) {
        if (!isScript(document)) {
          return undefined;
        }
        const project = projectFor(document.uri.fsPath)!;
        const limit = vscode.workspace.getConfiguration('modjs').get<number>('completion.limit', 4000);
        const result = completionsAt(document.getText(), document.offsetAt(position), project.index, limit);
        if (!result) {
          return undefined;
        }
        const range = new vscode.Range(document.positionAt(result.start), document.positionAt(result.end));
        const items = result.entries.map((entry) => {
          const item = new vscode.CompletionItem(entry.label, vscode.CompletionItemKind.Value);
          item.detail = entry.detail;
          item.documentation = new vscode.MarkdownString(entry.documentation);
          item.insertText = entry.insert;
          item.range = range;
          item.sortText = entry.sort;
          item.filterText = entry.label;
          return item;
        });
        return new vscode.CompletionList(items, result.truncated);
      },
    }, "'", '"', '`', ':', '#', '/'),

    vscode.languages.registerHoverProvider(selector, {
      provideHover(document, position) {
        if (!isScript(document)) {
          return undefined;
        }
        const project = projectFor(document.uri.fsPath)!;
        const text = hoverAt(document.getText(), document.offsetAt(position), project.index);
        return text ? new vscode.Hover(new vscode.MarkdownString(text)) : undefined;
      },
    }),

    vscode.languages.registerDefinitionProvider(selector, {
      provideDefinition(document, position) {
        if (!isScript(document)) {
          return undefined;
        }
        const project = projectFor(document.uri.fsPath)!;
        const text = document.getText();
        const literal = stringAt(text, document.offsetAt(position));
        if (!literal) {
          return undefined;
        }
        const target = resolveTarget(callContext(text, literal.quoteStart), text.slice(0, literal.quoteStart));
        const matches = identify(project.index, literal.value, target);
        return matches.map((match) => {
          const uri = vscode.Uri.from({ scheme: SCHEME, path: `/${match.kind}/${match.name.replace(/[|:]/g, '_')}.json`, query: `${match.kind}\n${match.name}\n${project.root}` });
          return new vscode.Location(uri, new vscode.Position(0, 0));
        });
      },
    }),

    vscode.workspace.registerTextDocumentContentProvider(SCHEME, {
      provideTextDocumentContent(uri) {
        const [kind, name, root] = uri.query.split('\n');
        const project = projects.get(root);
        return project ? project.index.entryText(kind, name) : '';
      },
    }),

    vscode.commands.registerCommand('modjs.refreshDump', () => {
      reloadAll();
      const project = [...projects.values()][0];
      if (project.index.loaded) {
        void vscode.window.showInformationMessage(`Dump loaded: ${statusText(project.index)}, ${project.index.meta?.generatedAt}.`);
      } else {
        void vscode.window.showWarningMessage(`No dump in ${project.probe}. Run /modjs dump in the game.`);
      }
    }),

    vscode.commands.registerCommand('modjs.setupProject', async () => {
      for (const root of projects.keys()) {
        await checkProjectFile(root);
      }
    }),
  );

  vscode.workspace.textDocuments.forEach(refreshDiagnostics);
}

export function deactivate(): void {
  projects.clear();
}
