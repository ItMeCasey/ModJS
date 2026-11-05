"""Runs the script-info screenshot tour on a real game client for one build and saves the frames to docs/frames.

Usage: python scripts/info_tour.py <module> [<module> ...]     e.g. neoforge-26.3 forge-1.20.1

For each build it copies a flat test world (made by Show Me What This Is's own tour) into <module>/run/saves, puts the SMWTI and Jade
jars into <module>/run/mods, copies examples/block-info into <module>/run/modjs, starts `gradlew runClient -PquickWorld=...`, moves the
window to DISPLAY6, and captures that monitor each time the tour script logs `INFO-TOUR READY <scene>`. The game is muted and nobody
needs to touch the keyboard or mouse. The tour script, the mods and the world are removed from run/ afterwards only if this script put
them there (the world copy is replaced on every run).

Jade has no build for Forge 26.x or Forge 1.21.1, so those builds show SMWTI only.
"""
import ctypes
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

from PIL import ImageGrab

ROOT = Path(__file__).resolve().parent.parent
DESKTOP = ROOT.parent
PLACE = r"C:\Users\casey\.claude\tools\place-window.ps1"
GRADLE_CACHE = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "curse.maven"
FRAMES = ROOT / "docs" / "frames"
TIMEOUT = 420
WORLD = "modjs_info"
OPTIONS = (
    "soundCategory_master:0.0\nmusic:0.0\npauseOnLostFocus:false\nonboardAccessibility:false\n"
    "skipMultiplayerWarning:true\ntutorialStep:none\nfullscreen:false\nguiScale:3\nlang:en_us\n"
)
# SMWTI full jar per build and Jade file id (None: Jade has no build for that loader and version).
JADE = {
    "neoforge-26.3": "9042207", "neoforge-26.2": "8755115", "neoforge-26.1.2": "9156414", "neoforge-1.21.1": "8591319",
    "forge-1.20.1": "8479276", "neoforge-1.20.1": "8479276",
}


def smwti_jar(module):
    loader, mc = module.split("-", 1)
    folder = DESKTOP / "SMWTI" / "versions" / f"mc{mc}" / loader / "build" / "libs"
    jars = sorted(j for j in folder.glob(f"smwti-{loader}-{mc}-*.jar") if "sources" not in j.name and "slim" not in j.name)
    return jars[-1] if jars else None


def smwti_world(module):
    loader, mc = module.split("-", 1)
    return DESKTOP / "SMWTI" / "versions" / f"mc{mc}" / loader / "run" / "saves" / "smwti_shots"


def jade_jar(module):
    file_id = JADE.get(module)
    if not file_id:
        return None
    return next(GRADLE_CACHE.glob(f"jade-324717/{file_id}/*/jade-324717-{file_id}.jar"), None)


def display_bounds():
    script = ("Add-Type -AssemblyName System.Windows.Forms; "
              "$b=([System.Windows.Forms.Screen]::AllScreens | Where-Object { $_.DeviceName -like '*DISPLAY6' } | Select-Object -First 1).Bounds; "
              "\"$($b.X) $($b.Y) $($b.Width) $($b.Height)\"")
    out = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True).stdout.split()
    x, y, w, h = (int(v) for v in out)
    return x, y, x + w, y + h


def my_window(module):
    """The visible window of the game started from this module's folder, or None; other sessions' games are never touched."""
    import psutil
    folder = str(ROOT / module).lower()
    pids = set()
    for proc in psutil.process_iter(["pid", "name", "cmdline", "cwd"]):
        try:
            if (proc.info["name"] or "").lower().startswith("java") and (str(proc.info["cwd"] or "").lower().startswith(folder)
                                                                      or any(folder in part.lower() for part in proc.info["cmdline"] or [])):
                pids.add(proc.info["pid"])
        except (psutil.Error, OSError):
            continue
    found = []
    user32 = ctypes.windll.user32

    @ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
    def visit(hwnd, _):
        if user32.IsWindowVisible(hwnd):
            pid = ctypes.c_ulong()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value in pids:
                length = user32.GetWindowTextLengthW(hwnd)
                buffer = ctypes.create_unicode_buffer(length + 1)
                user32.GetWindowTextW(hwnd, buffer, length + 1)
                if buffer.value.startswith("Minecraft"):
                    found.append(hwnd)
        return True

    user32.EnumWindows(visit, 0)
    return found[0] if found else None


def place(hwnd, bounds, topmost):
    """Fills DISPLAY6 with the window; topmost keeps another game's window from covering it while it is captured."""
    left, top, right, bottom = bounds
    flags = 0x0040  # SHOWWINDOW
    ctypes.windll.user32.ShowWindow(hwnd, 9)
    ctypes.windll.user32.SetWindowPos(hwnd, -1 if topmost else -2, left, top, right - left, bottom - top, flags)


def grab(hwnd, bounds):
    place(hwnd, bounds, True)
    time.sleep(0.6)
    image = ImageGrab.grab(bbox=bounds, all_screens=True)
    place(hwnd, bounds, False)
    return image


def prepare(module):
    folder = ROOT / module
    run = folder / "run"
    run.mkdir(exist_ok=True)
    (run / "options.txt").write_text(OPTIONS)
    world = run / "saves" / WORLD
    if world.exists():
        shutil.rmtree(world)
    shutil.copytree(smwti_world(module), world)
    mods = run / "mods"
    mods.mkdir(exist_ok=True)
    for old in mods.glob("smwti-*.jar"):
        old.unlink()
    for old in mods.glob("jade-*.jar"):
        old.unlink()
    shutil.copy2(smwti_jar(module), mods)
    jade = jade_jar(module)
    if jade:
        shutil.copy2(jade, mods / jade.name)
    # Other scripts in the dev run folder (the CI smoke scripts) are set aside for the tour and put back by cleanup().
    for folder in ("startup_scripts", "server_scripts", "client_scripts"):
        for script in (run / "modjs" / folder).glob("*.js"):
            script.rename(script.with_suffix(".js.tourbak"))
    startup = run / "modjs" / "startup_scripts"
    server = run / "modjs" / "server_scripts"
    startup.mkdir(parents=True, exist_ok=True)
    server.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "examples" / "block-info" / "startup_scripts" / "block_info.js", startup)
    shutil.copy2(ROOT / "examples" / "block-info" / "tour" / "info_tour.js", server)
    return run, jade is not None


def cleanup(run):
    for path in (run / "modjs" / "startup_scripts" / "block_info.js", run / "modjs" / "server_scripts" / "info_tour.js"):
        path.unlink(missing_ok=True)
    for saved in (run / "modjs").glob("*/*.js.tourbak"):
        saved.rename(saved.with_name(saved.name[:-len(".tourbak")]))


def tour(module):
    run, has_jade = prepare(module)
    FRAMES.mkdir(parents=True, exist_ok=True)
    bounds = display_bounds()
    log = []
    saved = []
    done = threading.Event()
    proc = subprocess.Popen(["cmd", "/c", str(ROOT / module / "gradlew.bat"), "runClient", "--no-daemon", "-q", f"-PquickWorld={WORLD}"],
                            cwd=ROOT / module, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")

    def pump():
        for line in proc.stdout:
            line = line.rstrip()
            log.append(line)
            match = re.search(r"INFO-TOUR READY (\w+)", line)
            if match:
                time.sleep(1.0)
                hwnd = my_window(module)
                if hwnd is None:
                    continue
                name = f"info-{module}-{match.group(1)}.png"
                grab(hwnd, bounds).save(FRAMES / name)
                saved.append(name)
            if "INFO-TOUR DONE" in line:
                done.set()

    threading.Thread(target=pump, daemon=True).start()
    def keep_placed():
        # Moves only this build's own game window onto DISPLAY6 as soon as it exists.
        placed = None
        for _ in range(360):
            hwnd = my_window(module)
            if hwnd and hwnd != placed:
                place(hwnd, bounds, False)
                placed = hwnd
            if done.is_set() or proc.poll() is not None:
                return
            time.sleep(1.0)

    threading.Thread(target=keep_placed, daemon=True).start()
    start = time.time()
    while proc.poll() is None and not done.is_set() and time.time() - start < TIMEOUT:
        time.sleep(2)
    timed_out = proc.poll() is None and not done.is_set()
    subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)], capture_output=True)
    (run / "info_tour.log").write_text("\n".join(log), encoding="utf-8")
    cleanup(run)
    errors = [line for line in log if " 0 error(s)" not in line and re.search(r"modjs/]: .*(Exception|Error)|ModLoadingException|Crash report saved|Exception in thread", line, re.I)]
    status = "TOUR PASS" if done.is_set() and len(saved) >= 4 and not errors else "TOUR FAIL"
    detail = "" if status == "TOUR PASS" else f": done={done.is_set()} timed_out={timed_out} frames={len(saved)} first_error={errors[:1]}"
    return f"{status} {module} (jade={'yes' if has_jade else 'no build'}, {len(saved)} frames, {int(time.time() - start)}s){detail}"


if __name__ == "__main__":
    ctypes.windll.user32.SetProcessDPIAware()
    bad = 0
    for module in sys.argv[1:]:
        result = tour(module)
        print(result, flush=True)
        bad += result.startswith("TOUR FAIL")
    sys.exit(1 if bad else 0)
