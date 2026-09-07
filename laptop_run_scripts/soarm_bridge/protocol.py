"""
Wire protocol between the phone app and this bridge.

Transport: WebSocket, one JSON object per text frame. Every frame has a "type".
This module is the single source of truth for that contract; the Android app must
match it.

PROTO_VERSION = 1

--------------------------------------------------------------------------------
phone -> bridge
--------------------------------------------------------------------------------
hello        {"type":"hello", "client":"soarm-android", "proto":1}
             First frame after connecting. Bridge replies with `welcome`.

mode         {"type":"mode", "mode":"jog"|"goto"|"sequence"|"voice"}
             Switch the active controller. Others go idle (hold position).

stop         {"type":"stop"}
             Emergency stop. Latches: the arm holds current joints and ignores
             motion until the next `mode` or `resume`.

resume       {"type":"resume"}
             Clear a latched stop.

ping         {"type":"ping", "t": <client ms>}   -> bridge replies `pong`.

--- Jog (streamed at ~20-30 Hz while the user touches a control) ---
jog          {"type":"jog",
              "vx": -1..1, "vy": -1..1,   # left stick, normalised
              "vz": -1..1,                # vertical slider
              "pitch": -1..1,             # wrist pitch bar   (- down, + up)
              "grip":  -1..1,             # gripper bar       (- close, + open)
              "speed": 0|1|2,             # slow / medium / fast chip
              "enabled": true}            # false = user released everything
             `enabled:false` or no frame within safety.watchdog_timeout_s -> hold.

--- Go To ---
goto         {"type":"goto",
              "x": <mm>, "y": <mm>, "z": <mm>,        # base frame
              "pitch": <deg>|null, "roll": <deg>|null}
             Bridge solves IK once and interpolates over goto.move_time_s.
             Replies `ack` {"of":"goto","ok":bool,"reason":str}.

--- Sequence ---
seq          {"type":"seq", "cmd":"release"}    torque off, move arm by hand
             {"type":"seq", "cmd":"hold"}       torque back on
             {"type":"seq", "cmd":"capture"}    store current joints as a pose
             {"type":"seq", "cmd":"clear"}      drop all captured poses
             {"type":"seq", "cmd":"play",
                 "steps":[ {"pose": <i>} | {"delay_ms": <n>}, ... ]}
             {"type":"seq", "cmd":"stop"}       halt playback
             Bridge replies `seq_state` after capture/clear/play/stop.

--- Voice ---
voice        {"type":"voice", "token":"stop"|"home"|"open"|"close"
                              |"up"|"down"|"left"|"right"|"forward"|"back"
                              |"faster"|"slower"}
             Discrete keyword. Directional tokens nudge for voice.pulse_ms.
             Replies `ack` {"of":"voice","ok":bool,"reason":str}.

--------------------------------------------------------------------------------
bridge -> phone
--------------------------------------------------------------------------------
welcome      {"type":"welcome", "proto":1, "robot":"so101",
              "joints":[...names...], "kinematics": true}

state        {"type":"state",                       # ~control.telemetry_hz
              "mode":"jog", "stopped":false, "connected":true,
              "joints": {"shoulder_pan": <deg>, ...},
              "ee": {"x":<m>, "y":<m>, "z":<m>} | null}

ack          {"type":"ack", "of":"goto"|"voice", "ok":true, "reason":""}
seq_state    {"type":"seq_state", "captured":<n>, "playing":false}
error        {"type":"error", "msg": "..."}
pong         {"type":"pong", "t": <echoed client ms>}
"""

from __future__ import annotations

import json
from typing import Any

PROTO_VERSION = 1


def decode(text: str) -> dict[str, Any]:
    msg = json.loads(text)
    if not isinstance(msg, dict) or "type" not in msg:
        raise ValueError("frame is not an object with a 'type'")
    return msg


def encode(msg: dict[str, Any]) -> str:
    return json.dumps(msg, separators=(",", ":"))


# --- builders for outbound frames -------------------------------------------------

def welcome(robot: str, joints: list[str], kinematics: bool) -> dict:
    return {
        "type": "welcome",
        "proto": PROTO_VERSION,
        "robot": robot,
        "joints": joints,
        "kinematics": kinematics,
    }


def state(mode: str, stopped: bool, joints: dict[str, float], ee: dict | None) -> dict:
    return {
        "type": "state",
        "mode": mode,
        "stopped": stopped,
        "connected": True,
        "joints": {k: round(v, 3) for k, v in joints.items()},
        "ee": ({k: round(v, 4) for k, v in ee.items()} if ee else None),
    }


def ack(of: str, ok: bool, reason: str = "") -> dict:
    return {"type": "ack", "of": of, "ok": ok, "reason": reason}


def seq_state(captured: int, playing: bool) -> dict:
    return {"type": "seq_state", "captured": captured, "playing": playing}


def error(msg: str) -> dict:
    return {"type": "error", "msg": msg}


def pong(t: Any) -> dict:
    return {"type": "pong", "t": t}
