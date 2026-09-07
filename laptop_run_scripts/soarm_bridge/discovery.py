"""
UDP discovery responder so the phone can find this bridge without an IP address.

The app broadcasts the probe MAGIC to udp/8766 on the local subnet; we reply with
a small JSON blob that includes the WebSocket port. Dependency-free; works on a
phone hotspot, a laptop hotspot, and a normal home router (all /24 subnets where
limited broadcast is delivered).
"""

from __future__ import annotations

import json
import logging
import socket
import threading

log = logging.getLogger("soarm.discovery")

DISCOVERY_PORT = 8766
MAGIC = b"SOARM-DISCOVER-1"


def start_discovery_responder(ws_port: int, robot: str) -> threading.Thread:
    reply = json.dumps({"soarm": True, "ws_port": int(ws_port), "robot": str(robot)}).encode()

    def run() -> None:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        except (AttributeError, OSError):
            pass
        try:
            s.bind(("", DISCOVERY_PORT))
        except OSError as e:
            log.warning("discovery responder not started (%s)", e)
            return
        log.info("discovery responder on udp/%d", DISCOVERY_PORT)
        while True:
            try:
                data, addr = s.recvfrom(1024)
            except OSError:
                return
            if data.strip() == MAGIC:
                log.info("discovery probe from %s -> replying", addr[0])
                try:
                    s.sendto(reply, addr)
                except OSError as e:
                    log.warning("discovery reply to %s failed: %s", addr[0], e)

    t = threading.Thread(target=run, name="discovery", daemon=True)
    t.start()
    return t
