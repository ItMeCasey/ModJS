/** Event groups and event names, read from the declarations ModJS writes (`probe/modjs.d.ts`). */
export type EventTable = Map<string, Set<string>>;

export function parseEvents(declarations: string): EventTable {
  const table: EventTable = new Map();
  const block = /declare const (\w+Events)\s*:\s*\{([\s\S]*?)\n\};/g;
  for (const match of declarations.matchAll(block)) {
    const names = new Set<string>();
    for (const member of match[2].matchAll(/^\s+(\w+)\s*[(<]/gm)) {
      names.add(member[1]);
    }
    table.set(match[1], names);
  }
  return table;
}
