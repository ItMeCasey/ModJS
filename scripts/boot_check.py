"""Starts one real game client for a build, waits until it reaches the main menu or crashes, then closes it and prints the result.

Usage: python scripts/boot_check.py <module> [--root <repo folder>]     e.g. neoforge-26.3, or --root C:\\...\\ModJS-tag for the tagged code

One launch only, no input: the window is moved to DISPLAY6 with Win32 calls on this game's own window, and the client is always
closed before the script returns. Options keep popups off. Nothing touches the cursor or keyboard.
"""
import ctypes
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

import psutil

OPTIONS = (
    "soundCategory_master:0.0\nmusic:0.0\npauseOnLostFocus:false\nonboardAccessibility:false\nskipMultiplayerWarning:true\n"
    "tutorialStep:none\njoinedFirstServer:true\nfullscreen:false\nguiScale:3\nlang:en_us\n"
)
CRASH = re.compile(r"Crash report saved|ModLoadingException|Game crashed|Exception in thread \"main\"")
READY = re.compile(r"Sound engine started")
TIMEOUT = 300


def window_of(folder):
    folder = str(folder).lower()
    pids = set()
    for proc in psutil.process_iter(["pid", "name", "cmdline", "cwd"]):
        try:
            if (proc.info["name"] or "").lower().startswith("java") and (
                    str(proc.info["cwd"] or "").lower().startswith(folder) or any(folder in p.lower() for p in proc.info["cmdline"] or [])):
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


def display6():
    script = ("Add-Type -AssemblyName System.Windows.Forms; "
              "$b=([System.Windows.Forms.Screen]::AllScreens | Where-Object { $_.DeviceName -like '*DISPLAY6' } | Select-Object -First 1).Bounds; "
              "\"$($b.X) $($b.Y) $($b.Width) $($b.Height)\"")
    x, y, w, h = (int(v) for v in subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True).stdout.split())
    return x, y, w, h


def main():
    args = sys.argv[1:]
    root = Path(__file__).resolve().parent.parent
    if "--root" in args:
        root = Path(args[args.index("--root") + 1])
        args = [a for a in args if a not in ("--root", str(root))]
    module = args[0]
    folder = root / module
    run = folder / "run"
    run.mkdir(exist_ok=True)
    (run / "options.txt").write_text(OPTIONS)
    x, y, w, h = display6()
    log = []
    proc = subprocess.Popen(["cmd", "/c", str(folder / "gradlew.bat"), "runClient", "--no-daemon", "-q"], cwd=folder,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
    state = {"result": None}

    def pump():
        for line in proc.stdout:
            line = line.rstrip()
            log.append(line)
            if state["result"] is None and CRASH.search(line):
                state["result"] = "CRASH"
            elif state["result"] is None and READY.search(line):
                state["result"] = "MENU"

    threading.Thread(target=pump, daemon=True).start()
    start = time.time()
    placed = None
    while proc.poll() is None and state["result"] is None and time.time() - start < TIMEOUT:
        hwnd = window_of(folder)
        if hwnd and hwnd != placed:
            ctypes.windll.user32.SetWindowPos(hwnd, -2, x, y, w, h, 0x0040)
            placed = hwnd
        time.sleep(1.0)
    time.sleep(3.0)  # let a crash report finish printing
    result = state["result"] or ("EXITED" if proc.poll() is not None else "TIMEOUT")
    subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)], capture_output=True)
    (run / "boot_check.log").write_text("\n".join(log), encoding="utf-8")
    cause = ""
    if result != "MENU":
        for i, line in enumerate(log):
            if re.search(r"ModLoadingException|Exception in thread|Caused by|NullPointerException|IllegalState", line) and "oshi" not in line:
                cause = " | ".join(s.strip() for s in log[i:i + 3])[:400]
                break
    print(f"{result} {module} ({int(time.time() - start)}s) {cause}")


if __name__ == "__main__":
    main()
