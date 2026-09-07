#!/usr/bin/env python
"""
Serve the browser viewer + the assets/ folder over HTTP.

    python scripts/serve_viewer.py            # http://localhost:8080/viewer/
    python scripts/serve_viewer.py --port 9000 --no-open

Normally you don't need this — `run_bridge.py` already serves the viewer. Use it
only to run the page without the bridge. The viewer connects to the bridge's
WebSocket itself (default ws://127.0.0.1:8765) and mirrors `state` telemetry onto
the model. Run the bridge separately:

    python run_bridge.py --target sim
"""

from __future__ import annotations

import argparse
import functools
import http.server
import socketserver
import threading
import webbrowser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent   # laptop_run_scripts/


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--no-open", action="store_true")
    args = ap.parse_args()

    if not list((ROOT / "assets").glob("*.urdf")):
        print("warning: no .urdf in assets/ — the 3D model won't load")

    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(ROOT))
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", args.port), handler) as httpd:
        url = f"http://localhost:{args.port}/viewer/"
        print(f"serving {ROOT} at {url}  (Ctrl-C to stop)")
        if not args.no_open:
            threading.Timer(0.6, lambda: webbrowser.open(url)).start()
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
