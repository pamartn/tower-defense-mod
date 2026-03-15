#!/usr/bin/env python3
"""Simple HTTP server that serves the Tower Defense mod JAR on the LAN."""

import os
import socket
import sys
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import quote

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
MODS_DIR = Path(__file__).parent / "server" / "mods"
MOD_PREFIX = "tower-defense"


def get_lan_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
        except OSError:
            return "127.0.0.1"


def find_mod_jar():
    for f in sorted(MODS_DIR.iterdir()):
        if f.name.startswith(MOD_PREFIX) and f.suffix == ".jar":
            return f
    return None


class ModHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        jar = find_mod_jar()
        if jar is None:
            self.send_error(404, "No tower-defense mod JAR found in server/mods/")
            return

        if self.path == f"/{quote(jar.name)}":
            self._serve_file(jar)
        else:
            self._serve_landing(jar)

    def _serve_landing(self, jar: Path):
        stat = jar.stat()
        size_kb = stat.st_size / 1024
        mtime = datetime.fromtimestamp(stat.st_mtime)
        build_date = mtime.strftime("%B %d, %Y at %I:%M %p")
        html = f"""\
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tower Defense Mod</title>
<style>
  * {{ margin:0; padding:0; box-sizing:border-box }}
  body {{ font-family:system-ui,sans-serif; background:#1a1a2e; color:#e0e0e0;
         display:flex; justify-content:center; align-items:center; min-height:100vh }}
  .card {{ background:#16213e; border-radius:12px; padding:2.5rem; text-align:center;
           max-width:420px; width:90%; box-shadow:0 8px 32px rgba(0,0,0,.4) }}
  h1 {{ font-size:1.6rem; margin-bottom:.5rem }}
  .version {{ color:#888; margin-bottom:1.5rem }}
  a.btn {{ display:inline-block; background:#0f3460; color:#e94560; text-decoration:none;
           padding:.75rem 2rem; border-radius:8px; font-weight:600; font-size:1.1rem;
           transition:background .2s }}
  a.btn:hover {{ background:#1a4a7a }}
  .info {{ margin-top:1.2rem; font-size:.85rem; color:#666 }}
</style>
</head>
<body>
<div class="card">
  <h1>Tower Defense Mod</h1>
  <p class="version">{jar.name} &mdash; {size_kb:.0f} KB</p>
  <p class="version">Last build: {build_date}</p>
  <a class="btn" href="/{quote(jar.name)}">Download</a>
  <p class="info">Place the .jar in your <code>.minecraft/mods/</code> folder.<br>Requires Fabric Loader + Fabric API.</p>
</div>
</body>
</html>"""
        data = html.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _serve_file(self, jar: Path):
        size = jar.stat().st_size
        self.send_response(200)
        self.send_header("Content-Type", "application/java-archive")
        self.send_header("Content-Disposition", f'attachment; filename="{jar.name}"')
        self.send_header("Content-Length", str(size))
        self.end_headers()
        with open(jar, "rb") as f:
            while chunk := f.read(65536):
                self.wfile.write(chunk)
        addr = self.client_address[0]
        print(f"  -> {addr} downloaded {jar.name}")

    def log_message(self, fmt, *args):
        pass


def main():
    jar = find_mod_jar()
    if jar is None:
        print(f"Error: no {MOD_PREFIX}*.jar found in {MODS_DIR}")
        sys.exit(1)

    ip = get_lan_ip()
    server = HTTPServer(("0.0.0.0", PORT), ModHandler)
    print(f"Serving Tower Defense mod on LAN:")
    print(f"  http://{ip}:{PORT}")
    print(f"\nFile: {jar.name} ({jar.stat().st_size / 1024:.0f} KB)")
    print(f"\nPress Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
        server.server_close()


if __name__ == "__main__":
    main()
