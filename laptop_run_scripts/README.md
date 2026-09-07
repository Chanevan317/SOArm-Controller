# laptop_run_scripts — the SOArm bridge

The laptop side of **SOArm Controller**: receives commands from the phone over a
WebSocket, solves inverse kinematics with
[LeRobot](https://huggingface.co/docs/lerobot), and drives an SO‑100 / SO‑101 arm
over USB. Also serves a live 3‑D viewer.

**Full guide: [`../docs/running-the-bridge.md`](../docs/running-the-bridge.md)**
(env setup, the run modes, config, viewer, first run against real hardware).
For how the phone reaches it — discovery, firewall, Wi‑Fi —
see [`../docs/connecting.md`](../docs/connecting.md).

Quick reference:

```bash
source ~/.venvs/lerobot/bin/activate

python run_bridge.py --demo                        # sim, no phone — self‑driving demo
python run_bridge.py                               # sim + the real phone app
python run_bridge.py --target hardware --config config.yaml   # real arm + phone
```

Ports: **tcp/8765** (control), **udp/8766** (discovery), **:8080** (viewer).

The phone↔bridge message format is the module docstring in
`soarm_bridge/protocol.py`.

This is a **first pass** — the limits, speeds and gains in `config.example.yaml`
are conservative placeholders to tune against the real arm.
