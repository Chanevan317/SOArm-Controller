# Connecting the phone to the laptop

The app talks to the laptop bridge over a WebSocket on **TCP port 8765**. It
finds the laptop **by itself** — you don't type an IP address. This page covers
how that works, the firewall step that trips people up, and what to do when it
won't connect.

---

## The short version

1. Phone and laptop on the **same Wi‑Fi** (home router, phone hotspot, or laptop
   hotspot — all fine).
2. On the laptop, **open the ports** if a firewall is running (see below).
3. Run the bridge: `python run_bridge.py` (see
   [running-the-bridge.md](running-the-bridge.md)).
4. Open the app. It searches the network and connects on its own. The connection
   pill in the top bar goes **Linked**.

---

## How discovery works

- The bridge listens for a small **UDP broadcast** on port **8766**.
- The app broadcasts a probe to every network it's on; the bridge replies with
  its address and the WebSocket port.
- The app then opens the WebSocket to that address.
- It also tries `127.0.0.1:8765` first, which covers a **USB cable** setup.

So two ports matter: **TCP 8765** (control + telemetry) and **UDP 8766**
(discovery).

---

## Open the firewall (laptop)

If the laptop runs a firewall that blocks incoming connections, discovery and the
WebSocket are both dropped and the app just sits on *"Searching…"*. This is the
single most common cause.

### Linux — ufw

```bash
sudo ufw status                      # is it active?
sudo ufw allow 8765/tcp
sudo ufw allow 8766/udp
```

### Linux — firewalld

```bash
sudo firewall-cmd --state
sudo firewall-cmd --add-port=8765/tcp --add-port=8766/udp        # this session
sudo firewall-cmd --permanent --add-port=8765/tcp --add-port=8766/udp
sudo firewall-cmd --reload
```

### Windows

Windows Firewall is on by default. The **first** time `python` listens on a port
it pops a *"Allow access?"* dialog — tick **Private networks** and allow. If you
missed it, or the network is classed *Public*:

```
Settings → Network & internet → Wi‑Fi → (your network) → set to Private
```

then re‑run the bridge and allow the prompt. Or add rules in *Windows Defender
Firewall → Advanced → Inbound Rules* for TCP 8765 and UDP 8766.

### macOS

The application firewall is off by default. If it's on, it prompts per‑app —
allow `python`/`Python` incoming connections when asked.

---

## Wi‑Fi options

All three work. Discovery is a per‑interface broadcast, so it doesn't care which.

| Setup | Notes |
| --- | --- |
| **Home / office router** | Simplest. Both devices join the same network. Some *guest* networks isolate clients from each other — use the main network. |
| **Phone hotspot** (laptop joins the phone) | Reliable, no router needed. The phone is the gateway; the laptop gets an address like `192.168.x.x`. |
| **Laptop hotspot** (phone joins the laptop) | Also fine. The laptop is a fixed gateway (`10.42.0.1` on NetworkManager, `192.168.137.1` on Windows). |

Mobile data can stay on — the phone uses it for internet and the hotspot/Wi‑Fi
for the arm at the same time.

---

## USB cable (no Wi‑Fi)

Lowest latency, no firewall or discovery involved. Needs **USB debugging** on the
phone (Settings → Developer options).

```bash
adb reverse tcp:8765 tcp:8765
```

Now `127.0.0.1:8765` on the phone tunnels to the laptop. The app tries this
address automatically, so it just connects. Re‑run `adb reverse` after each
replug.

---

## Manual entry (fallback)

If auto‑discovery fails (locked‑down corporate Wi‑Fi that blocks broadcast, say):

1. Get the laptop's Wi‑Fi address — the bridge prints a `manual fallback: X.X.X.X`
   line, or run `hostname -I` (Linux) / `ipconfig` (Windows).
2. In the app: tap the connection pill → **Enter manually** → that address, port
   `8765` → **Connect to this address**.

---

## Android 16 — local network permission

targetSdk 37 turns on **Local Network Protection**. The first time the app
touches the LAN, Android asks to *"allow access to devices on your local
network"*. Allow it. If you missed the prompt:

```
Settings → Apps → SOArm Controller → Permissions → Local network devices → Allow
```

The one‑time permission screen after the welcome page also nudges for this.

---

## Won't connect — checklist

Work top to bottom.

1. **Bridge running?** `python run_bridge.py` shows
   `listening on ws://0.0.0.0:8765` and `discovery responder on udp/8766`.

2. **USB test (isolates everything network).**
   `adb reverse tcp:8765 tcp:8765`, then app → *Enter manually* → `127.0.0.1`,
   `8765`.
   - **Connects** → the app and bridge are fine; it's a firewall or Wi‑Fi issue
     below.
   - **Fails** → the app build or the bridge; grab
     `adb logcat | grep -iE 'soarm|okhttp'` while connecting.

3. **Firewall.** Check `sudo iptables -L -n | head` — if you see
   `Chain INPUT (policy DROP)` and `ufw-*` chains, ufw is blocking. Open the
   ports (above). This is usually it.

4. **Bridge log while the app searches.** Does
   `discovery probe from 192.168.x.x -> replying` appear?
   - **No** → the probe isn't reaching the laptop → firewall inbound, or the two
     devices aren't actually on the same network.
   - **Yes, but the app doesn't connect** → the reply or the follow‑up WebSocket
     is blocked → open **both** ports, and grant the Android 16 local‑network
     permission.

5. **Right network?** On the laptop, `ip -4 addr show` (Linux) — the Wi‑Fi
   interface address should be in the same subnet the phone's Wi‑Fi shows.

6. **Client isolation.** If it's a router you don't control (café, campus), it
   may block device‑to‑device traffic entirely. Use a phone hotspot instead.
