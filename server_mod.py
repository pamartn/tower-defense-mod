#!/usr/bin/env python3
"""
Build and deploy tower-defense mod to Minecraft server.
Usage:
  python server_mod.py           # build + deploy
  python server_mod.py --build   # build only
  python server_mod.py --deploy  # deploy only (assumes build exists)

Configure via environment:
  MOD_SERVER_HOST   - e.g. pi@raspberrypi.local or user@192.168.1.x
  MOD_SERVER_PATH   - remote mods folder, e.g. ~/mc/mods
  MOD_SERVER_RESTART - if set, run restart command after deploy
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAR_NAME = "tower-defense-1.1.0.jar"
JAR_PATH = SCRIPT_DIR / "build" / "libs" / JAR_NAME


def build() -> bool:
    """Run gradle build."""
    print("Building mod...")
    result = subprocess.run(
        ["./gradlew", "clean", "build"],
        cwd=SCRIPT_DIR,
        capture_output=False,
    )
    if result.returncode != 0:
        print("Build failed.", file=sys.stderr)
        return False
    if not JAR_PATH.exists():
        print(f"Jar not found: {JAR_PATH}", file=sys.stderr)
        return False
    print(f"Built: {JAR_PATH}")
    return True


def deploy() -> bool:
    """Deploy jar to remote server via scp."""
    host = os.environ.get("MOD_SERVER_HOST", "pi@raspberrypi.local")
    path = os.environ.get("MOD_SERVER_PATH", "~/mc/mods")
    dest = f"{host}:{path}/"
    print(f"Deploying to {dest}...")
    result = subprocess.run(
        ["scp", str(JAR_PATH), dest],
        capture_output=False,
    )
    if result.returncode != 0:
        print("Deploy failed.", file=sys.stderr)
        return False
    print("Deployed.")
    return True


def restart_server() -> bool:
    """Restart Minecraft server on remote host."""
    host = os.environ.get("MOD_SERVER_HOST", "pi@raspberrypi.local")
    cmd = os.environ.get("MOD_SERVER_RESTART", "sudo systemctl restart minecraft")
    print(f"Restarting server on {host}...")
    result = subprocess.run(
        ["ssh", host, cmd],
        capture_output=False,
    )
    if result.returncode != 0:
        print("Restart failed.", file=sys.stderr)
        return False
    print("Server restarted.")
    return True


def main():
    parser = argparse.ArgumentParser(description="Build and deploy tower-defense mod")
    parser.add_argument("--build", action="store_true", help="Build only")
    parser.add_argument("--deploy", action="store_true", help="Deploy only")
    parser.add_argument("--restart", action="store_true", help="Restart server after deploy")
    args = parser.parse_args()

    do_build = args.build or (not args.build and not args.deploy)
    do_deploy = args.deploy or (not args.build and not args.deploy)
    do_restart = args.restart or bool(os.environ.get("MOD_SERVER_RESTART"))

    ok = True
    if do_build:
        ok = build() and ok
    if ok and do_deploy:
        if not JAR_PATH.exists():
            print("Jar missing. Run with --build first.", file=sys.stderr)
            sys.exit(1)
        ok = deploy() and ok
    if ok and do_restart:
        ok = restart_server() and ok

    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
