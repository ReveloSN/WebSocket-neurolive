from __future__ import annotations

import logging
from datetime import datetime

import httpx

from app.config import Settings
from app.schemas import AckMessage, DeviceCommandMessage
from app.state import TelemetrySnapshot


LOGGER = logging.getLogger("neurolive.realtime.backend")


# Maneja la integracion HTTP con el backend principal.
class BackendClient:
    def __init__(self, settings: Settings) -> None:
        # Conserva la configuracion compartida del cliente.
        self._settings = settings
        self._client: httpx.AsyncClient | None = None

    # Inicializa el cliente HTTP si la integracion esta habilitada.
    async def start(self) -> None:
        if not self._settings.backend_base_url:
            return

        timeout_seconds = max(self._settings.integration_timeout_ms / 1000.0, 0.5)
        self._client = httpx.AsyncClient(
            base_url=self._settings.backend_base_url.rstrip("/"),
            timeout=timeout_seconds,
            headers={"X-Internal-Token": self._settings.internal_token},
        )

    # Cierra el cliente HTTP reutilizable.
    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None

    # Valida el token localmente o contra el backend.
    async def validate_token(self, device_id: str, token: str) -> bool:
        if self._settings.device_tokens:
            return self._settings.device_tokens.get(device_id) == token

        if self._client is None:
            LOGGER.warning("Remote token validation skipped because backend client is disabled")
            return False

        try:
            response = await self._client.post(
                "/internal/devices/validate-token",
                json={"deviceId": device_id, "token": token},
            )
            response.raise_for_status()
            return bool(response.json())
        except httpx.HTTPError as exception:
            LOGGER.warning("Token validation failed for deviceId=%s: %s", device_id, exception)
            return False

    # Reenvia telemetria biometrica al backend.
    async def forward_telemetry(self, snapshot: TelemetrySnapshot) -> None:
        if self._client is None:
            return

        await self._post_safely(
            "/internal/telemetry",
            {
                "deviceId": snapshot.device_id,
                "bpm": snapshot.bpm,
                "spo2": snapshot.spo2,
                "sensorConnected": snapshot.sensor_connected,
                "deviceTimestamp": snapshot.device_timestamp,
                "receivedAt": snapshot.received_at.isoformat(),
            },
            context=f"telemetry deviceId={snapshot.device_id}",
        )

    # Notifica al backend que un dispositivo se autentico.
    async def notify_authenticated(self, device_id: str, occurred_at: datetime) -> None:
        if self._client is None:
            return

        await self._post_safely(
            f"/internal/devices/{device_id}/authenticated",
            {"occurredAt": occurred_at.isoformat()},
            context=f"auth deviceId={device_id}",
        )

    # Notifica desconexiones del canal realtime al backend.
    async def notify_disconnected(self, device_id: str, reason: str, occurred_at: datetime) -> None:
        if self._client is None:
            return

        await self._post_safely(
            f"/internal/devices/{device_id}/disconnected",
            {"reason": reason, "occurredAt": occurred_at.isoformat()},
            context=f"disconnect deviceId={device_id}",
        )

    # Registra el envio de un comando sin acoplarse al backend.
    async def notify_command_sent(self, device_id: str, command: DeviceCommandMessage) -> None:
        LOGGER.info(
            "Command sent deviceId=%s commandId=%s action=%s",
            device_id,
            command.commandId,
            command.action,
        )

    # Registra el ACK recibido desde el dispositivo.
    async def notify_command_acknowledged(self, ack_message: AckMessage) -> None:
        LOGGER.info(
            "Command ack deviceId=%s commandId=%s status=%s",
            ack_message.deviceId,
            ack_message.commandId,
            ack_message.status,
        )

    # Ejecuta POST internos sin romper el loop principal.
    async def _post_safely(self, path: str, payload: dict[str, object], context: str) -> None:
        if self._client is None:
            return

        try:
            response = await self._client.post(path, json=payload)
            response.raise_for_status()
        except httpx.HTTPError as exception:
            LOGGER.warning("Backend integration failed for %s: %s", context, exception)
