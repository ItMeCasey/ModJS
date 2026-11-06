"""Waits (up to 10 minutes, polling every 30 s) until no game client of another project is running, then exits 0; exits 1 on timeout."""
import subprocess
import sys
import time

TOOL = r"C:\Users\casey\.claude\tools\who-owns-minecraft.ps1"
deadline = time.time() + 600
while True:
    out = subprocess.run(["powershell", "-NoProfile", "-File", TOOL], capture_output=True, text=True).stdout
    clients = [line for line in out.splitlines() if " game " in line]
    if not clients:
        sys.exit(0)
    print("waiting for:", clients[0].strip()[:120], flush=True)
    if time.time() > deadline:
        sys.exit(1)
    time.sleep(30)
