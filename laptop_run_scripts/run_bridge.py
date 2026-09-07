#!/usr/bin/env python
"""
SOArm bridge — one command runs the WebSocket bridge, the 3D viewer, and
(optionally) a motion demo.

    source ~/.venvs/lerobot/bin/activate

    python run_bridge.py                      # sim + viewer, opens the browser
    python run_bridge.py --demo              # ... and wiggles the arm so you see it move
    python run_bridge.py --target hardware   # drive the real arm (robot.port in config)

Flags:
    --target sim|hardware   sim (default) = no arm, joints in memory, IK still real
    --demo                  drive a gentle looping motion in-process (sim or hw)
    --no-viewer             don't serve the 3D page
    --no-open               serve it but don't open the browser
    --config PATH           config.yaml (defaults baked in otherwise)
"""

from __future__ import annotations

import argparse
import asyncio
import functools
import http.server
import logging
import signal
import socketserver
import subprocess
import sys
import threading
import socket
import webbrowser
from pathlib import Path

from soarm_bridge import config as cfgmod
from soarm_bridge.arm import Arm
from soarm_bridge.loop import ControlLoop
from soarm_bridge.safety import Safety
from soarm_bridge.server import Server

ROOT = Path(__file__).resolve().parent


def _lan_ips() -> list[str]:
    """Best-effort list of this machine's non-loopback IPv4 addresses."""
    ips: set[str] = set()
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127."):
                ips.add(ip)
    except OSError:
        pass
    # UDP-connect trick: the address the OS would use to reach an external host
    for probe in ("192.168.1.1", "10.0.0.1", "8.8.8.8"):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect((probe, 80))
            ips.add(s.getsockname()[0])
            s.close()
        except OSError:
            pass
    return sorted(i for i in ips if not i.startswith("127."))


class _QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_a):  # noqa: D401 - silence per-request logging
        pass


class _ViewerHTTP(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def server_bind(self):
        # http.server.HTTPServer.server_bind() calls socket.getfqdn(), which does
        # a reverse-DNS lookup that HANGS with no network. Bind without it.
        socketserver.TCPServer.server_bind(self)
        host, port = self.server_address[:2]
        self.server_name = host or "localhost"
        self.server_port = port


def start_viewer_http(port: int) -> _ViewerHTTP:
    handler = functools.partial(_QuietHandler, directory=str(ROOT))
    httpd = _ViewerHTTP(("", port), handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="SOArm laptop bridge")
    p.add_argument("--target", choices=["sim", "hardware"], default="sim",
                   help="sim (default): no arm. hardware: drive the real arm.")
    p.add_argument("--sim", action="store_true", help=argparse.SUPPRESS)  # legacy alias
    p.add_argument("--demo", action="store_true",
                   help="drive a gentle looping motion so you can watch it move")
    p.add_argument("--no-viewer", action="store_true", help="don't serve the 3D page")
    p.add_argument("--no-open", action="store_true", help="don't open the browser")
    p.add_argument("--no-fetch", action="store_true",
                   help="don't auto-download the viewer meshes if they're missing")
    p.add_argument("--viewer-port", type=int, default=8080)
    p.add_argument("--config", default=None, help="path to config.yaml")
    p.add_argument("--port", default=None, help="override robot.port")
    p.add_argument("--robot", default=None, choices=["so100", "so101"], help="override robot.type")
    p.add_argument("--no-kinematics", action="store_true", help="disable IK even if a URDF exists")
    p.add_argument("-v", "--verbose", action="store_true")
    return p.parse_args()


async def main() -> None:
    args = parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
        force=True,   # reclaim the root logger from lerobot's import-time config
    )
    logging.getLogger("soarm").setLevel(logging.DEBUG if args.verbose else logging.INFO)
    log = logging.getLogger("soarm")

    sim = args.sim or args.target == "sim"
    if args.sim:
        log.warning("--sim is deprecated; use --target sim")

    cfg = cfgmod.load(args.config)
    if args.port:
        cfg.robot.port = args.port
    if args.robot:
        cfg.robot.type = args.robot
    if args.no_kinematics:
        cfg.kinematics.enabled = False

    log.info("target: %s%s", "SIM (no hardware)" if sim else f"HARDWARE {cfg.robot.port}",
             "  + demo" if args.demo else "")

    arm = Arm(
        robot_type=cfg.robot.type,
        port=cfg.robot.port,
        robot_id=cfg.robot.id,
        use_degrees=cfg.robot.use_degrees,
        sim=sim,
        urdf_path=(str(cfg.urdf_path) if cfg.kinematics.enabled else None),
        target_frame=cfg.kinematics.target_frame,
        home_joints_deg=cfg.home_joints_deg,
    )
    arm.connect()

    safety = Safety(cfg.safety)
    loop_obj = ControlLoop(arm, safety, cfg)
    server = Server(loop_obj, cfg.server.host, cfg.server.port)
    loop_obj.server = server

    from soarm_bridge.discovery import start_discovery_responder

    start_discovery_responder(cfg.server.port, cfg.robot.type)

    ips = _lan_ips()
    log.info("phone -> just open the app; it finds this bridge on the network.")
    if ips:
        log.info("        (manual fallback: %s  port %d)", ips[0], cfg.server.port)

    httpd = None
    if not args.no_viewer:
        if not args.no_fetch:
            from soarm_bridge.meshes import ensure_meshes

            have, total = ensure_meshes(ROOT / "assets", cfg.robot.type,
                                        progress=lambda m: log.info("meshes: %s", m))
            if total and have < total:
                log.warning("meshes: %d/%d — viewer will show a stick figure for the rest", have, total)
        try:
            httpd = start_viewer_http(args.viewer_port)
            url = f"http://localhost:{args.viewer_port}/viewer/"
            log.info("viewer: %s", url)
            if not args.no_open:
                threading.Timer(1.0, lambda: webbrowser.open(url)).start()
        except OSError as e:
            log.warning("viewer http server not started (%s)", e)

    stop_event = asyncio.Event()

    def _shutdown(*_):
        log.info("shutdown requested")
        stop_event.set()

    running_loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        running_loop.add_signal_handler(sig, _shutdown)

    tasks = [
        asyncio.create_task(server.serve_forever(), name="server"),
        asyncio.create_task(loop_obj.run(), name="control-loop"),
    ]

    demo_proc = None
    if args.demo:
        # drive the bridge exactly like the phone would — a real WebSocket client
        demo_proc = subprocess.Popen(
            [sys.executable, str(ROOT / "scripts" / "fake_phone.py"),
             "--loop", "--port", str(cfg.server.port)]
        )
        log.info("demo: fake_phone.py --loop (pid %d)", demo_proc.pid)

    try:
        await stop_event.wait()
    finally:
        if demo_proc is not None:
            demo_proc.terminate()
        loop_obj.stop_loop()
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        if httpd is not None:
            httpd.shutdown()
        arm.disconnect()          # disables torque
        log.info("stopped cleanly")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
