"""Build and self-test each independent module, preserving local scripts and mods."""
import shutil
import subprocess
import sys
from pathlib import Path
from world_setup import peaceful_world
ROOT = Path(__file__).resolve().parent.parent

def verify(module):
    folder = ROOT / module
    run = folder / 'run'
    backups, generated = [], []
    try:
        for pattern in ('modjs/**/*.js', 'mods/*.jar'):
            for original in run.glob(pattern):
                backup = original.with_name(original.name + '.verifybak')
                if backup.exists():
                    raise RuntimeError(f'Unrestored backup: {backup}')
                original.rename(backup)
                backups.append((original, backup))
        for source in (ROOT / 'smoke-test').glob('*_scripts/*.js'):
            target = run / 'modjs' / source.parent.name / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            generated.append(target)
        for path in run.glob('gametestserver/**/level.dat'):
            peaceful_world(path.parent)
        # The loaded hook also covers brand-new game-test worlds before any player enters.
        settings = run / 'modjs/server_scripts/test_world_settings.js'
        settings.write_text("""ServerEvents.loaded(event => {
  Utils.runCommand('difficulty peaceful');
  try { GameRule.set('spawn_mobs', false); }
  catch (error) { GameRule.set('doMobSpawning', false); }
});
""", encoding='utf-8')
        generated.append(settings)
        for tasks, name in ((['build'], 'final-build'), (['runGameTestServer'], 'final-selftest')):
            log = ROOT / 'release' / f'{name}-{module}.log'
            with log.open('w', encoding='utf-8') as output:
                result = subprocess.run(['cmd', '/c', 'gradlew.bat', *tasks, '--no-daemon'], cwd=folder,
                    stdout=output, stderr=subprocess.STDOUT, timeout=480)
            text = log.read_text(encoding='utf-8', errors='replace')
            if result.returncode or (name == 'final-selftest' and ('SELFTEST DONE failures=0' not in text or 'SELFTEST FAIL' in text)):
                raise RuntimeError(f'{module} {name} failed; see {log}\n' + text[-2500:])
            print(f'PASS {module} {name}', flush=True)
            if name == 'final-selftest':
                lines = [line for line in text.splitlines() if 'SELFTEST' in line]
                (ROOT / 'docs/testing' / f'selftest-{module}.log').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    finally:
        for target in generated:
            target.unlink(missing_ok=True)
        for original, backup in reversed(backups):
            backup.rename(original)

if __name__ == '__main__':
    for module in sys.argv[1:]:
        verify(module)
