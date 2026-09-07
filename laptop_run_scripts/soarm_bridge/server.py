"""WebSocket server. One text frame = one JSON message (see protocol.py)."""

from __future__ import annotations

import asyncio
import logging

from websockets.asyncio.server import serve

from . import protocol

log = logging.getLogger("soarm.server")


class Client:
    def __init__(self, ws):
        self.ws = ws
        self.addr = getattr(ws, "remote_address", None)
        self._q: asyncio.Queue[str] = asyncio.Queue(maxsize=128)

    def enqueue(self, text: str) -> None:
        try:
            self._q.put_nowait(text)
        except asyncio.QueueFull:
            try:                       # drop the oldest (stale telemetry) and retry
                self._q.get_nowait()
                self._q.put_nowait(text)
            except Exception:  # noqa: BLE001
                pass

    async def writer(self) -> None:
        try:
            while True:
                await self.ws.send(await self._q.get())
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001 - client went away
            pass


class Server:
    def __init__(self, loop_obj, host: str, port: int):
        self.loop = loop_obj
        self.host = host
        self.port = port
        self.clients: set[Client] = set()

    def broadcast(self, text: str) -> None:
        for c in list(self.clients):
            c.enqueue(text)

    async def _handler(self, ws) -> None:
        client = Client(ws)
        self.clients.add(client)
        writer = asyncio.create_task(client.writer())
        log.info("client connected: %s", client.addr)
        self.loop.on_connect(client)
        try:
            async for raw in ws:
                try:
                    msg = protocol.decode(raw)
                except Exception as e:  # noqa: BLE001
                    client.enqueue(protocol.encode(protocol.error(f"bad frame: {e}")))
                    continue
                try:
                    self.loop.dispatch(msg, client)
                except Exception as e:  # noqa: BLE001 - never let one frame kill the socket
                    log.exception("dispatch")
                    client.enqueue(protocol.encode(protocol.error(f"dispatch: {e}")))
        except Exception:  # noqa: BLE001
            pass
        finally:
            writer.cancel()
            self.clients.discard(client)
            self.loop.on_disconnect(client)
            log.info("client gone: %s", client.addr)

    async def serve_forever(self) -> None:
        log.info("listening on ws://%s:%d", self.host, self.port)
        async with serve(self._handler, self.host, self.port):
            await asyncio.Future()
