"""Real-client tour of custom namespaces: a block and an item in nsdemo: and the same under modjs: in one frame, plus language checks.

Run from ModJS/scripts: python namespace_tour.py <module>. Starts the client through launch-isolated.ps1 (hidden desktop),
uses a fresh copy of the module tour world, installs examples/namespaces and the tour scripts, and restores everything afterwards.
Writes docs/testing/namespace-<module>.log and docs/frames/namespace-<module>.png.
"""
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAUNCHER = Path.home() / '.claude/tools/launch-isolated.ps1'
EXAMPLE = ROOT / 'examples/namespaces'
DESKTOP = 'ModJSNamespace'


def ps(*args):
    return subprocess.run(['powershell', '-NoProfile', '-File', str(LAUNCHER), *args], capture_output=True, text=True)


def syntax_check(path):
    rhino = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.mozilla/rhino/1.7.15').glob('*/*.jar'))
    result = subprocess.run(['java', '-cp', str(rhino), 'org.mozilla.javascript.tools.shell.Main', '-opt', '-1', '-version', '200', '-e',
                             'new Function(readFile(' + json.dumps(str(path)) + '));'], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f'{path}: {result.stderr}')


def tour(module):
    from world_setup import peaceful_world
    folder = ROOT / module
    run = folder / 'run'
    world_name = 'modjs_ns_' + str(time.time_ns())
    world = run / 'saves' / world_name
    shutil.copytree(run / 'saves/modjs_info', world, ignore=lambda directory, names: set(names) & {'playerdata', 'stats', 'advancements'})
    peaceful_world(world, reset_player=True)
    backups, generated = [], []
    shots = run / 'screenshots'
    for stale in ('ns_world.png', 'ns_stall.png'):
        (shots / stale).unlink(missing_ok=True)
    try:
        for pattern in ('modjs/**/*.js', 'mods/*.jar'):
            for original in run.glob(pattern):
                backup = original.with_name(original.name + '.overlaybak')
                if backup.exists():
                    raise RuntimeError(f'Backup already exists: {backup}')
                original.rename(backup)
                backups.append((original, backup))
        installs = [
            (EXAMPLE / 'startup_scripts/namespaces.js', run / 'modjs/startup_scripts/namespaces.js'),
            (EXAMPLE / 'tour/namespace_tour.js', run / 'modjs/server_scripts/namespace_tour.js'),
            (EXAMPLE / 'tour/namespace_tour_client.js', run / 'modjs/client_scripts/namespace_tour_client.js'),
        ]
        for source in sorted((EXAMPLE / 'assets').rglob('*.png')):
            installs.append((source, run / 'modjs/assets' / source.relative_to(EXAMPLE / 'assets')))
        for source, target in installs:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            generated.append(target)
            if target.suffix == '.js':
                syntax_check(target)
        latest = run / 'logs/latest.log'
        previous = latest.stat().st_mtime_ns if latest.exists() else 0
        launch_log = Path.home() / f'AppData/Local/launch-isolated/{DESKTOP}.log'
        previous_launch = launch_log.stat().st_mtime_ns if launch_log.exists() else 0
        result = ps('-Run', '-Desktop', DESKTOP, '-WorkDir', str(folder), '-Command',
                    rf'.\gradlew.bat runClient --no-daemon -q -PquickWorld={world_name} -PnoSmwti')
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        deadline = time.monotonic() + 420
        log = ''
        while time.monotonic() < deadline:
            time.sleep(5)
            if launch_log.exists() and launch_log.stat().st_mtime_ns != previous_launch:
                output = launch_log.read_text(encoding='utf-8', errors='replace')
                if 'BUILD FAILED' in output or 'Game crashed!' in output:
                    raise RuntimeError(output[-3000:])
            if not latest.exists() or latest.stat().st_mtime_ns == previous:
                continue
            log = latest.read_text(encoding='utf-8', errors='replace')
            if 'NS-TOUR clean client exit' in log and 'Stopping!' in log:
                time.sleep(5)
                break
            if 'NS-CHECK FAIL' in log:
                break
            if any('[ERROR]' in line and ('namespaces' in line or 'namespace_tour' in line) for line in log.splitlines()):
                raise RuntimeError('\n'.join(line for line in log.splitlines() if '[ERROR]' in line and ('namespaces' in line or 'namespace_tour' in line)))
            if 'Crash report saved' in log or 'Game crashed!' in log or 'Failed to load level data' in log:
                raise RuntimeError(f'Client failed; inspect {latest}')
        else:
            raise RuntimeError(f'{module} timed out; inspect {latest}')
        log = latest.read_text(encoding='utf-8', errors='replace')
        lines = [line for line in log.splitlines() if 'NS-CHECK' in line or 'NS-TOUR' in line]
        evidence = ROOT / 'docs/testing' / f'namespace-{module}.log'
        evidence.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        missing = [token for token in ('NS-TOUR READY world', 'NS-CHECK PASS lang item.nsdemo.gem', 'NS-CHECK PASS lang item.modjs.gem',
                                       'NS-CHECK PASS lang block.nsdemo.crystal', 'NS-CHECK PASS lang block.modjs.crystal', 'NS-CHECK PASS lang item.nsdemo.shard', 'NS-CHECK ok frame')
                   if token not in log]
        shot = shots / 'ns_world.png'
        if shot.exists():
            shutil.copy2(shot, ROOT / 'docs/frames' / f'namespace-{module}.png')
        else:
            missing.append('world screenshot')
        if missing:
            raise RuntimeError(f'{module}: missing {missing}; see {evidence}')
        print(f'PASS {module} namespace tour', flush=True)
    finally:
        ps('-Kill', '-Desktop', DESKTOP, '-WorkDir', str(folder))
        for target in generated:
            target.unlink(missing_ok=True)
        for original, backup in reversed(backups):
            backup.rename(original)
        shutil.rmtree(world, ignore_errors=True)


if __name__ == '__main__':
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    for name in sys.argv[1:]:
        tour(name)
