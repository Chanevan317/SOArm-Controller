#!/usr/bin/env python
"""
Emit the frames the Android app will send, so the bridge (and the browser viewer)
can be driven and tested without the app — which has no transport yet.

    source ~/.venvs/lerobot/bin/activate
    python run_bridge.py --sim -v                 # terminal 1
    python scripts/fake_phone.py                  # terminal 2  (runs a scripted scenario)
    python scripts/fake_phone.py --scenario jog   # just one section
    python scripts/fake_phone.py --host 192.168.1.50

Scenarios exercise realistic rates and the edge cases the smoke test skipped:
streaming jog, enable/disable re-seed, a watchdog gap, partial frames, rapid mode
flips, e-stop + resume.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import math
import time

from websockets.asyncio.client import connect

PRINT_STATE_EVERY = 15


class Phone:
    def __init__(self, ws):
        self.ws = ws
        self._n = 0

    async def send(self, d: dict) -> None:
        await self.ws.send(json.dumps(d))

    async def rx_loop(self) -> None:
        try:
            async for raw in self.ws:
                m = json.loads(raw)
                t = m.get("type")
                if t == "state":
                    self._n += 1
                    if self._n % PRINT_STATE_EVERY == 0:
                        ee = m.get("ee")
                        ee_s = f"ee=({ee['x']:+.3f},{ee['y']:+.3f},{ee['z']:+.3f})" if ee else "ee=--"
                        print(f"  state  mode={m['mode']:<8} stop={m['stopped']!s:<5} {ee_s}")
                elif t in ("welcome", "ack", "seq_state", "error", "pong"):
                    print(f"  <- {json.dumps(m)}")
        except Exception:
            pass


async def sc_jog(p: Phone) -> None:
    print("[jog] mode + 4 s of streamed circular stick @ 25 Hz")
    await p.send({"type": "mode", "mode": "jog"})
    t0 = time.monotonic()
    while time.monotonic() - t0 < 4.0:
        a = (time.monotonic() - t0) * 1.5
        await p.send({
            "type": "jog",
            "vx": 0.7 * math.cos(a), "vy": 0.7 * math.sin(a), "vz": 0.0,
            "pitch": 0.0, "grip": 0.0, "speed": 1, "enabled": True,
        })
        await asyncio.sleep(0.04)

    print("[jog] release (enabled:false) for 1 s, then re-enable (should re-seed, not jump)")
    for _ in range(25):
        await p.send({"type": "jog", "vx": 0, "vy": 0, "vz": 0, "pitch": 0,
                      "grip": 0, "speed": 1, "enabled": False})
        await asyncio.sleep(0.04)

    print("[jog] 2 s of +Z with a 0.8 s silent gap in the middle (watchdog should hold)")
    for i in range(50):
        if 18 <= i <= 38:                       # ~0.8 s of no frames
            await asyncio.sleep(0.04)
            continue
        await p.send({"type": "jog", "vx": 0, "vy": 0, "vz": 0.6, "pitch": 0,
                      "grip": 0.4, "speed": 2, "enabled": True})
        await asyncio.sleep(0.04)

    print("[jog] partial frame (only vx) — bridge should treat missing fields as 0")
    await p.send({"type": "jog", "vx": 0.5, "enabled": True})
    await asyncio.sleep(0.5)


async def sc_goto(p: Phone) -> None:
    print("[goto] reachable target, then a deliberately unreachable one")
    await p.send({"type": "mode", "mode": "goto"})
    await asyncio.sleep(0.2)
    await p.send({"type": "goto", "x": 270, "y": 20, "z": 160})
    await asyncio.sleep(3.0)
    await p.send({"type": "goto", "x": 60, "y": 0, "z": 90})
    await asyncio.sleep(1.0)


async def sc_sequence(p: Phone) -> None:
    print("[sequence] release, capture x3, hold, play with delays, stop midway")
    await p.send({"type": "mode", "mode": "sequence"})
    await p.send({"type": "seq", "cmd": "release"})
    await asyncio.sleep(0.2)
    for _ in range(3):
        await p.send({"type": "seq", "cmd": "capture"})
        await asyncio.sleep(0.2)
    await p.send({"type": "seq", "cmd": "hold"})
    await asyncio.sleep(0.2)
    await p.send({"type": "seq", "cmd": "play",
                  "steps": [{"pose": 0}, {"delay_ms": 400}, {"pose": 1},
                            {"delay_ms": 400}, {"pose": 2}]})
    await asyncio.sleep(1.5)
    await p.send({"type": "seq", "cmd": "stop"})
    await asyncio.sleep(0.4)


async def sc_voice(p: Phone) -> None:
    print("[voice] home, directional pulses, faster, e-stop, unknown token, resume")
    await p.send({"type": "mode", "mode": "voice"})
    for tok in ["home", "forward", "left", "faster", "up", "close"]:
        await p.send({"type": "voice", "token": tok})
        await asyncio.sleep(0.9)
    await p.send({"type": "voice", "token": "stop"})
    await asyncio.sleep(0.3)
    await p.send({"type": "voice", "token": "wobble"})
    await asyncio.sleep(0.2)
    await p.send({"type": "resume"})
    await asyncio.sleep(0.3)


async def sc_flip(p: Phone) -> None:
    print("[flip] rapid mode switching + interleaved commands")
    for _ in range(6):
        for mode in ("jog", "goto", "sequence", "voice"):
            await p.send({"type": "mode", "mode": mode})
            await p.send({"type": "jog", "vx": 0.3, "enabled": True})   # wrong-mode frame, should be ignored
            await asyncio.sleep(0.05)
    await asyncio.sleep(0.3)


SCENARIOS = {
    "jog": sc_jog, "goto": sc_goto, "sequence": sc_sequence,
    "voice": sc_voice, "flip": sc_flip,
}


# smooth subset for a repeating showcase (skips the edge-case scenarios)
LOOP_ORDER = ["jog", "goto", "voice"]


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--scenario", choices=list(SCENARIOS) + ["all"], default="all")
    ap.add_argument("--loop", action="store_true",
                    help="repeat jog/goto/voice forever (used by run_bridge.py --demo)")
    args = ap.parse_args()

    uri = f"ws://{args.host}:{args.port}"
    print(f"connecting {uri}")
    ws = None
    for _ in range(12):                       # bridge may still be starting
        try:
            ws = await connect(uri)
            break
        except OSError:
            await asyncio.sleep(0.5)
    if ws is None:
        print("could not reach the bridge")
        return

    try:
        p = Phone(ws)
        rx = asyncio.create_task(p.rx_loop())
        await p.send({"type": "hello", "client": "fake-phone", "proto": 1})
        await asyncio.sleep(0.3)

        if args.loop:
            while True:
                for name in LOOP_ORDER:
                    print(f"\n=== {name} ===")
                    await SCENARIOS[name](p)
                await asyncio.sleep(1.0)
        else:
            order = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
            for name in order:
                print(f"\n=== scenario: {name} ===")
                await SCENARIOS[name](p)
            print("\ndone; e-stopping and disconnecting")
            await p.send({"type": "stop"})
            await asyncio.sleep(0.3)
        rx.cancel()
    finally:
        await ws.close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
