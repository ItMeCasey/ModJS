"""Real-client tour of in-world crafting: item on block, items in a cauldron, and the recipe viewer pages.

Run from ModJS/scripts: python world_recipes_tour.py <module>. Starts the client through launch-isolated.ps1 (hidden desktop),
uses a fresh copy of the module tour world, installs examples/world-recipes and the tour scripts, and restores everything afterwards.
Writes docs/testing/worldrecipes-<module>.log and docs/frames/worldrecipes-<module>.png.
"""
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAUNCHER = Path.home() / '.claude/tools/launch-isolated.ps1'
EXAMPLE = ROOT / 'examples/world-recipes'
DESKTOP = 'ModJSWorldRecipes'


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
    world_name = 'modjs_wr_' + str(time.time_ns())
    world = run / 'saves' / world_name
    shutil.copytree(run / 'saves/modjs_info', world, ignore=lambda directory, names: set(names) & {'playerdata', 'stats', 'advancements'})
    peaceful_world(world, reset_player=True)
    backups, generated = [], []
    shots = run / 'screenshots'
    for stale in ('wr_world.png', 'wr_stall.png'):
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
            (EXAMPLE / 'tour/world_recipes_tour.js', run / 'modjs/server_scripts/world_recipes_tour.js'),
            (EXAMPLE / 'tour/world_recipes_tour_client.js', run / 'modjs/client_scripts/world_recipes_tour_client.js'),
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
            if 'WR-TOUR clean client exit' in log and 'Stopping!' in log:
                time.sleep(5)
                break
            if 'WR-CHECK FAIL' in log:
                break
            if any('[ERROR]' in line and ('world_recipes' in line) for line in log.splitlines()):
                raise RuntimeError('\n'.join(line for line in log.splitlines() if '[ERROR]' in line and ('world_recipes' in line)))
            if 'Crash report saved' in log or 'Game crashed!' in log or 'Failed to load level data' in log:
                raise RuntimeError(f'Client failed; inspect {latest}')
        else:
            raise RuntimeError(f'{module} timed out; inspect {latest}')
        log = latest.read_text(encoding='utf-8', errors='replace')
        lines = [line for line in log.splitlines() if 'WR-CHECK' in line or 'WR-TOUR' in line]
        evidence = ROOT / 'docs/testing' / f'worldrecipes-{module}.log'
        evidence.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        missing = [token for token in ('WR-TOUR READY world', 'WR-TOUR CLICKED stone', 'WR-TOUR DROPPED coal and sand', 'WR-CHECK PASS item on block', 'WR-CHECK PASS drops',
                                       'WR-CHECK PASS items in water', 'WR-CHECK PASS viewer page on stone', 'WR-CHECK PASS viewer pages on flint and diamond', 'WR-CHECK ok frame')
                   if token not in log]
        shot = shots / 'wr_world.png'
        if shot.exists():
            shutil.copy2(shot, ROOT / 'docs/frames' / f'worldrecipes-{module}.png')
        else:
            missing.append('world screenshot')
        if missing:
            raise RuntimeError(f'{module}: missing {missing}; see {evidence}')
        print(f'PASS {module} world recipes tour', flush=True)
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
