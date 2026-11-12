"""Real-client check of title screen buttons with item icons (GitHub issue 2: "Components not bound yet" crash on 26.3).

Run from ModJS/scripts: python title_icon_tour.py <module>. Starts the client through launch-isolated.ps1 (hidden desktop) with
examples/title-icon/tour installed, waits for the title screen frame and a clean exit, and restores everything afterwards.
Writes docs/testing/titleicon-<module>.log and docs/frames/titleicon-<module>.png.
"""
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAUNCHER = Path.home() / '.claude/tools/launch-isolated.ps1'
DESKTOP = 'ModJSTitleIcon'


def ps(*args):
    return subprocess.run(['powershell', '-NoProfile', '-File', str(LAUNCHER), *args], capture_output=True, text=True)


def tour(module):
    folder = ROOT / module
    run = folder / 'run'
    shots = run / 'screenshots'
    (shots / 'ti_title.png').unlink(missing_ok=True)
    backups, generated = [], []
    try:
        for pattern in ('modjs/**/*.js', 'mods/*.jar'):
            for original in run.glob(pattern):
                backup = original.with_name(original.name + '.overlaybak')
                if backup.exists():
                    raise RuntimeError(f'Backup already exists: {backup}')
                original.rename(backup)
                backups.append((original, backup))
        target = run / 'modjs/client_scripts/title_icon_tour_client.js'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / 'examples/title-icon/tour/title_icon_tour_client.js', target)
        generated.append(target)
        latest = run / 'logs/latest.log'
        previous = latest.stat().st_mtime_ns if latest.exists() else 0
        launch_log = Path.home() / f'AppData/Local/launch-isolated/{DESKTOP}.log'
        previous_launch = launch_log.stat().st_mtime_ns if launch_log.exists() else 0
        result = ps('-Run', '-Desktop', DESKTOP, '-WorkDir', str(folder), '-Command', r'.\gradlew.bat runClient --no-daemon -q -PnoSmwti')
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        deadline = time.monotonic() + 420
        while time.monotonic() < deadline:
            time.sleep(5)
            if launch_log.exists() and launch_log.stat().st_mtime_ns != previous_launch:
                output = launch_log.read_text(encoding='utf-8', errors='replace')
                if 'BUILD FAILED' in output or 'Game crashed!' in output:
                    raise RuntimeError(output[-3000:])
            if not latest.exists() or latest.stat().st_mtime_ns == previous:
                continue
            log = latest.read_text(encoding='utf-8', errors='replace')
            if 'TI-TOUR clean client exit' in log and 'Stopping!' in log:
                time.sleep(5)
                break
            if 'Crash report saved' in log or 'Game crashed!' in log:
                raise RuntimeError(f'Client failed; inspect {latest}')
        else:
            raise RuntimeError(f'{module} timed out; inspect {latest}')
        log = latest.read_text(encoding='utf-8', errors='replace')
        lines = [line for line in log.splitlines() if 'TI-' in line or 'Components not bound' in line]
        (ROOT / 'docs/testing' / f'titleicon-{module}.log').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        missing = [] if 'TI-CHECK frame taken' in log else ['frame']
        if 'Components not bound' in log:
            missing.append('Components not bound error in log')
        shot = shots / 'ti_title.png'
        if shot.exists():
            shutil.copy2(shot, ROOT / 'docs/frames' / f'titleicon-{module}.png')
        else:
            missing.append('screenshot')
        if missing:
            raise RuntimeError(f'{module}: {missing}')
        print(f'PASS {module} title icon tour', flush=True)
    finally:
        ps('-Kill', '-Desktop', DESKTOP, '-WorkDir', str(folder))
        for target in generated:
            target.unlink(missing_ok=True)
        for original, backup in reversed(backups):
            backup.rename(original)


if __name__ == '__main__':
    for name in sys.argv[1:]:
        tour(name)
