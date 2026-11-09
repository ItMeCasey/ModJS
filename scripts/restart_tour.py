"""Verify run-time dimension restoration across two clean client exits.

Run from ModJS/scripts. Uses a fresh copy of each module's existing tour world,
preserves all scripts and optional mods, and restores them even on failure.
"""
import subprocess
import time
import shutil
import sys
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAUNCHER = Path.home() / '.claude/tools/launch-isolated.ps1'
WORLD = 'modjs_restart_20261108'
EVIDENCE = 'restart'
SERVER = r'''
(function () {
  DimensionEvents.add(event => {
    event.addType('modjs:bright', {base: 'minecraft:overworld', set: {ambient_light: 0.5}});
    event.add('modjs:flat_test', {generator: 'flat', layers: [{block: 'minecraft:stone', height: 3}], type: 'modjs:bright'});
  });
  const id = 'modjs:restart_probe';
  const stage = STAGE;
  const check = (name, value) => console.log('RESTART-CHECK ' + (value ? 'PASS ' : 'FAIL ') + name);
  PlayerEvents.loggedOut(e => console.log('RESTART-CHECK EVENT loggedOut ' + e.playerName));
  PlayerEvents.chat(e => console.log('RESTART-CHECK EVENT chat ' + e.message));
  LevelEvents.unloaded(e => console.log('RESTART-CHECK EVENT unloaded'));
  PlayerEvents.loggedIn(e => {
    Utils.runAfter(40, () => {
      try {
        check('exists-before-create-stage-' + stage, Dimensions.exists(id) === (stage === 2));
        if (stage === 1) check('created', Dimensions.create(id, {generator: 'void'}));
        Utils.runCommand('gamemode creative ' + e.player.getName().getString());
        check('teleport-stage-' + stage, Dimensions.teleport(e.player, id, 0.5, 70, 0.5));
        Utils.runAfter(40, () => {
          try {
            const level = e.player.level();
            if (stage === 1) Utils.runCommand('execute in ' + id + ' run setblock 0 69 0 minecraft:diamond_block');
            const pos = new (Java.loadClass('net.minecraft.core.BlockPos'))(0, 69, 0);
            check('block-stage-' + stage, String(level.getBlockState(pos)).indexOf('diamond_block') >= 0);
            console.log('RESTART-CHECK READY stage-' + stage);
          } catch (err) { console.log('RESTART-CHECK FAIL block ' + err); }
        });
      } catch (err) { console.log('RESTART-CHECK FAIL driver ' + err); }
    });
  });
})();
'''
CLIENT = r'''
(function () {
  let ticks = -1;
  let waiting = 0;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) {
      const mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
      if (++waiting % 100 === 0) {
        let screen = null;
        try { screen = mc.gui.screen(); } catch (err) { screen = mc.screen; }
        if (screen != null) {
          console.log('RESTART-CHECK waiting screen ' + screen);
          Java.loadClass('com.itmecasey.modjs.client.ClientTweaks').autoConfirmExperimental(screen);
          const children = screen.children();
          const buttons = [];
          for (let i = 0; i < children.size(); i++) {
            let button = children.get(i);
            if (String(button).indexOf('Button') >= 0) buttons.push(button);
            try {
              let text = String(button.getMessage().getString());
              if (text === 'Proceed' || text === 'I know what I\'m doing!' || (String(screen).indexOf('LoadingErrorScreen') >= 0 && text === 'Proceed to main menu')) {
                try { button.onPress(); } catch (err) { button.onPress(null); }
                break;
              }
            } catch (err) {}
          }
          if (String(screen).indexOf('BackupConfirmScreen') >= 0 && buttons.length > 1) {
            console.log('RESTART-CHECK accepting backup prompt for copied test world');
            try { buttons[1].onPress(); } catch (err) { buttons[1].onPress(null); }
          }
        }
      }
      return;
    }
    if (++ticks === 240) {
      console.log('RESTART-CHECK clean client exit');
      Java.loadClass('net.minecraft.client.Minecraft').getInstance().stop();
    }
  });
})();
'''

def ps(*args):
    return subprocess.run(['powershell', '-NoProfile', '-File', str(LAUNCHER), *args], capture_output=True, text=True)

def fresh_player(world):
    from world_setup import peaceful_world
    peaceful_world(world, reset_player=True)


def tour(module, stages=(1, 2), extra_client='', flags=''):
    folder = ROOT / module
    run = folder / 'run'
    world = run / 'saves' / WORLD
    if world.exists():
        raise RuntimeError(f'Refusing to overwrite existing test world: {world}')
    shutil.copytree(run / 'saves/modjs_info', world,
        ignore=lambda directory, names: set(names) & {'playerdata', 'stats', 'advancements'})
    fresh_player(world)
    backups = []
    generated = []
    desktop = 'ModJSRestart'
    try:
        for pattern in ('modjs/**/*.js', 'mods/*.jar'):
            for original in run.glob(pattern):
                backup = original.with_name(original.name + '.restartbak')
                if backup.exists():
                    raise RuntimeError(f'Backup already exists: {backup}')
                original.rename(backup)
                backups.append((original, backup))
        for stage in stages:
            for relative, content in [('server_scripts/restart_probe.js', SERVER.replace('STAGE', str(stage))), ('client_scripts/restart_probe.js', CLIENT + extra_client)]:
                target = run / 'modjs' / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding='utf-8')
                if target not in generated:
                    generated.append(target)
                rhino = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.mozilla/rhino/1.7.15').glob('*/*.jar'))
                syntax = subprocess.run(['java', '-cp', str(rhino), 'org.mozilla.javascript.tools.shell.Main', '-opt', '-1', '-version', '200', '-e',
                    'new Function(readFile(' + json.dumps(str(target)) + '));'], capture_output=True, text=True)
                if syntax.returncode:
                    raise RuntimeError(f'{target}: {syntax.stderr}')
            latest = run / 'logs/latest.log'
            previous = latest.stat().st_mtime_ns if latest.exists() else 0
            launch_log = Path.home() / 'AppData/Local/launch-isolated' / f'{desktop}.log'
            previous_launch = launch_log.stat().st_mtime_ns if launch_log.exists() else 0
            result = ps('-Run', '-Desktop', desktop, '-WorkDir', str(folder), '-Command', rf'.\gradlew.bat runClient --no-daemon -q -PquickWorld={WORLD} -PnoSmwti {flags}')
            if result.returncode:
                raise RuntimeError(result.stdout + result.stderr)
            deadline = time.monotonic() + 360
            while time.monotonic() < deadline:
                time.sleep(2)
                if launch_log.exists() and launch_log.stat().st_mtime_ns != previous_launch:
                    output = launch_log.read_text(encoding='utf-8', errors='replace')
                    if 'BUILD FAILED' in output or 'Game crashed!' in output:
                        raise RuntimeError(output[-3000:])
                if not latest.exists() or latest.stat().st_mtime_ns == previous:
                    continue
                log = latest.read_text(encoding='utf-8', errors='replace')
                if 'RESTART-CHECK clean client exit' in log and 'RESTART-CHECK EVENT unloaded' in log and 'Stopping!' in log:
                    time.sleep(6)
                    break
                if 'RESTART-CHECK FAIL' in log:
                    raise RuntimeError('\n'.join(line for line in log.splitlines() if 'RESTART-CHECK' in line))
                if any('restart_probe.js:' in line and '[ERROR]' in line for line in log.splitlines()):
                    raise RuntimeError('\n'.join(line for line in log.splitlines() if 'restart_probe.js:' in line))
                if 'Failed to load level data' in log or 'Crash report saved' in log or 'Game crashed!' in log:
                    raise RuntimeError(f'Client failed to load; inspect {latest}')
            else:
                raise RuntimeError(f'{module} stage {stage} timed out; inspect {latest}')
            evidence = ROOT / 'docs/testing' / f'{EVIDENCE}-{module}-{stage}.log'
            evidence.parent.mkdir(parents=True, exist_ok=True)
            evidence.write_text('\n'.join(line for line in log.splitlines() if any(token in line for token in ('RESTART-CHECK', 'CLIENT-CHECK', 'JEI-CHECK', 'EMI-CHECK', 'REI-CHECK', 'ModJS created dimension modjs:restart_probe'))) + '\n', encoding='utf-8')
            required = [f'PASS exists-before-create-stage-{stage}', f'PASS teleport-stage-{stage}', f'PASS block-stage-{stage}', f'READY stage-{stage}', 'EVENT loggedOut', 'EVENT unloaded']
            if any(token not in log for token in required):
                raise RuntimeError(f'Missing checks: {[token for token in required if token not in log]}')
            print(f'PASS {module} stage {stage}', flush=True)
            ps('-Kill', '-Desktop', desktop)
    finally:
        ps('-Kill', '-Desktop', desktop)
        for target in generated:
            target.unlink(missing_ok=True)
        for original, backup in reversed(backups):
            backup.rename(original)

if __name__ == '__main__':
    for module in sys.argv[1:]:
        tour(module)
