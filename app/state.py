from __future__ import annotations

import asyncio
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from fastapi import WebSocket


# Retorna un timestamp consistente en UTC.
def utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass(slots=True)
# Conserva la ultima telemetria serializable del dispositivo.
class TelemetrySnapshot:
    device_id: str
    bpm: float
    spo2: float
    sensor_connected: bool
    device_timestamp: int
    received_at: datetime


@dataclass(slots=True)
# Representa una conexion WebSocket viva del ESP32.
class DeviceSession:
    websocket: WebSocket
    authenticated: bool = False
    device_id: str | None = None
    connected_at: datetime = field(default_factory=utc_now)
    last_activity_at: datetime = field(default_factory=utc_now)
    last_heartbeat_at: datetime | None = None
    send_lock: asyncio.Lock = field(default_factory=asyncio.Lock, repr=False)


@dataclass(slots=True)
# Resume el estado observable de un dispositivo.
class DeviceStatusRecord:
    device_id: str
    connected: bool = False
    state: str = "unknown"
    connected_at: datetime | None = None
    last_activity_at: datetime | None = None
    last_heartbeat_at: datetime | None = None
    disconnected_at: datetime | None = None
    disconnect_reason: str | None = None


# Administra sesiones activas y estado en memoria.
class DeviceRegistry:
    def __init__(self, history_limit: int) -> None:
        # Protege el estado compartido del runtime.
        self._lock = asyncio.Lock()
        self._history_limit = history_limit
        self._sessions: dict[str, DeviceSession] = {}
        self._statuses: dict[str, DeviceStatusRecord] = {}
        self._latest_telemetry: dict[str, TelemetrySnapshot] = {}
        self._telemetry_history: dict[str, deque[TelemetrySnapshot]] = {}

    # Registra una sesion autenticada y reemplaza la anterior si existe.
    async def register_authenticated(self, session: DeviceSession, device_id: str) -> DeviceSession | None:
        async with self._lock:
            replaced_session = self._sessions.get(device_id)
            now = utc_now()
            session.authenticated = True
            session.device_id = device_id
            session.connected_at = now
            session.last_activity_at = now
            self._sessions[device_id] = session

            status = self._statuses.get(device_id, DeviceStatusRecord(device_id=device_id))
            status.connected = True
            status.state = "connected"
            status.connected_at = now
            status.last_activity_at = now
            status.disconnected_at = None
            status.disconnect_reason = None
            self._statuses[device_id] = status
            return replaced_session if replaced_session is not session else None

    # Actualiza la ultima actividad del dispositivo.
    async def touch_activity(self, session: DeviceSession, heartbeat: bool = False) -> None:
        if session.device_id is None:
            return

        async with self._lock:
            current_session = self._sessions.get(session.device_id)
            if current_session is not session:
                return

            now = utc_now()
            session.last_activity_at = now
            status = self._statuses.get(session.device_id)
            if status is not None:
                status.last_activity_at = now
            if heartbeat:
                session.last_heartbeat_at = now
                if status is not None:
                    status.last_heartbeat_at = now

    # Guarda la ultima telemetria y su historial corto.
    async def store_telemetry(self, session: DeviceSession, snapshot: TelemetrySnapshot) -> None:
        if session.device_id is None:
            return

        async with self._lock:
            current_session = self._sessions.get(session.device_id)
            if current_session is not session:
                return

            session.last_activity_at = snapshot.received_at
            self._latest_telemetry[snapshot.device_id] = snapshot
            history = self._telemetry_history.setdefault(
                snapshot.device_id,
                deque(maxlen=self._history_limit),
            )
            history.appendleft(snapshot)

            status = self._statuses.get(snapshot.device_id)
            if status is not None:
                status.last_activity_at = snapshot.received_at

    # Marca la sesion como desconectada solo si aun es la activa.
    async def unregister_if_active(self, session: DeviceSession, reason: str) -> bool:
        if session.device_id is None:
            return False

        async with self._lock:
            current_session = self._sessions.get(session.device_id)
            if current_session is not session:
                return False

            self._sessions.pop(session.device_id, None)
            status = self._statuses.get(session.device_id, DeviceStatusRecord(device_id=session.device_id))
            status.connected = False
            status.state = "disconnected"
            status.disconnected_at = utc_now()
            status.disconnect_reason = reason
            self._statuses[session.device_id] = status
            return True

    # Retorna una sesion por device_id.
    async def get_session(self, device_id: str) -> DeviceSession | None:
        async with self._lock:
            return self._sessions.get(device_id)

    # Lista el estado de todos los conectados.
    async def list_connected_statuses(self) -> list[DeviceStatusRecord]:
        async with self._lock:
            connected_ids = set(self._sessions.keys())
            return [self._statuses[device_id] for device_id in connected_ids if device_id in self._statuses]

    # Retorna el estado mas reciente de un dispositivo.
    async def get_status(self, device_id: str) -> DeviceStatusRecord:
        async with self._lock:
            return self._statuses.get(device_id, DeviceStatusRecord(device_id=device_id))

    # Retorna la ultima telemetria registrada.
    async def get_latest_telemetry(self, device_id: str) -> TelemetrySnapshot | None:
        async with self._lock:
            return self._latest_telemetry.get(device_id)

    # Retorna el historial corto de telemetria.
    async def get_recent_history(self, device_id: str) -> list[TelemetrySnapshot]:
        async with self._lock:
            history = self._telemetry_history.get(device_id, deque())
            return list(history)

    # Busca sesiones que ya excedieron el timeout.
    async def get_stale_sessions(self, timeout_seconds: int) -> list[DeviceSession]:
        threshold = utc_now() - timedelta(seconds=timeout_seconds)
        async with self._lock:
            return [session for session in self._sessions.values() if session.last_activity_at < threshold]
