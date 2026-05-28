from __future__ import annotations

import asyncio
import json
import logging
import uuid

from fastapi import HTTPException, WebSocket, WebSocketDisconnect
from pydantic import ValidationError
from starlette.websockets import WebSocketState

from app.backend import BackendClient
from app.config import Settings
from app.crisis_predictor import CrisisPredictor
from app.schemas import (
    AckMessage,
    AuthMessage,
    CommandDispatchResponse,
    ConnectedDeviceResponse,
    DeviceCommandMessage,
    DeviceStatusResponse,
    ErrorMessage,
    HeartbeatMessage,
    LatestTelemetryResponse,
    LightCommandRequest,
    CommandPayload,
    TelemetryMessage,
    TelemetrySnapshotResponse,
)
from app.state import DeviceRegistry, DeviceSession, TelemetrySnapshot, utc_now


LOGGER = logging.getLogger("neurolive.realtime.gateway")


# Senala un cierre esperado del canal WebSocket.
class ConnectionClosed(Exception):
    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason


# Orquesta el gateway realtime y sus flujos principales.
class RealtimeGatewayService:
    def __init__(self, settings: Settings) -> None:
        # Conserva las dependencias del runtime asincrono.
        self.settings = settings
        self.registry = DeviceRegistry(history_limit=settings.telemetry_history_limit)
        self.backend = BackendClient(settings)
        self.predictor = CrisisPredictor(settings.prediction)
        self._stop_event = asyncio.Event()
        self._timeout_task: asyncio.Task[None] | None = None
        self._background_tasks: set[asyncio.Task[object]] = set()
        self._handlers = {
            "auth": self._handle_auth,
            "telemetry": self._handle_telemetry,
            "heartbeat": self._handle_heartbeat,
            "ack": self._handle_ack,
        }

    # Arranca el cliente backend y el monitor de heartbeats.
    async def start(self) -> None:
        await self.backend.start()
        self._timeout_task = asyncio.create_task(self._monitor_timeouts())

    # Cierra tareas en segundo plano del servicio.
    async def stop(self) -> None:
        self._stop_event.set()
        if self._timeout_task is not None:
            await self._timeout_task

        if self._background_tasks:
            for task in list(self._background_tasks):
                task.cancel()
            await asyncio.gather(*self._background_tasks, return_exceptions=True)

        await self.backend.close()
        await self.predictor.close()

    # Atiende una conexion WebSocket del ESP32.
    async def handle_connection(self, websocket: WebSocket) -> None:
        await websocket.accept()
        session = DeviceSession(websocket=websocket)
        close_reason = "client_disconnected"

        try:
            while True:
                raw_message = await websocket.receive_text()
                await self._route_message(session, raw_message)
        except WebSocketDisconnect as exception:
            close_reason = self._resolve_disconnect_reason(exception.code)
        except ConnectionClosed as exception:
            close_reason = exception.reason
        except Exception:
            LOGGER.exception("Unexpected WebSocket transport error")
            close_reason = "transport_error"
            await self._try_close(session.websocket, 1011, "Transport error")
        finally:
            await self._cleanup_session(session, close_reason)

    # Lista los conectados con una vista compacta.
    async def get_connected_devices(self) -> list[ConnectedDeviceResponse]:
        statuses = await self.registry.list_connected_statuses()
        return [
            ConnectedDeviceResponse(
                deviceId=status.device_id,
                state=status.state,
                connectedAt=status.connected_at,
                lastActivityAt=status.last_activity_at,
                lastHeartbeatAt=status.last_heartbeat_at,
            )
            for status in sorted(statuses, key=lambda item: item.device_id)
        ]

    # Retorna el estado detallado de un dispositivo.
    async def get_device_status(self, device_id: str) -> DeviceStatusResponse:
        status = await self.registry.get_status(device_id)
        return DeviceStatusResponse(
            deviceId=status.device_id,
            state=status.state,
            connected=status.connected,
            connectedAt=status.connected_at,
            lastActivityAt=status.last_activity_at,
            lastHeartbeatAt=status.last_heartbeat_at,
            disconnectedAt=status.disconnected_at,
            disconnectReason=status.disconnect_reason,
        )

    # Retorna la telemetria mas reciente y el historial corto.
    async def get_latest_telemetry(self, device_id: str) -> LatestTelemetryResponse:
        latest = await self.registry.get_latest_telemetry(device_id)
        history = await self.registry.get_recent_history(device_id)
        return LatestTelemetryResponse(
            deviceId=device_id,
            latestTelemetry=self._build_telemetry_response(latest),
            recentHistory=[self._build_telemetry_response(snapshot) for snapshot in history],
        )

    # Despacha un comando de luz al dispositivo conectado.
    async def send_light_command(
        self,
        device_id: str,
        request: LightCommandRequest,
    ) -> CommandDispatchResponse:
        session = await self.registry.get_session(device_id)
        if session is None:
            raise HTTPException(status_code=404, detail="Device is not connected")

        command = DeviceCommandMessage(
            commandId=f"cmd-{uuid.uuid4().hex[:12]}",
            payload=CommandPayload.model_validate(request.model_dump()),
        )
        await self._send_json(session, command.model_dump())
        self._spawn_task(self.backend.notify_command_sent(device_id, command))

        return CommandDispatchResponse(
            deviceId=device_id,
            commandId=command.commandId,
            action=command.action,
            status="sent",
        )

    # Retorna la configuracion de contingencia del hardware.
    def get_fallback_config(self) -> dict[str, object]:
        fallback = self.settings.fallback
        return {
            "ledColor": fallback.led_color,
            "ledIntensity": fallback.led_intensity,
            "ledMode": fallback.led_mode,
            "heartbeatIntervalSeconds": fallback.heartbeat_interval_seconds,
            "description": fallback.description,
        }

    # Enruta mensajes entrantes segun su tipo.
    async def _route_message(self, session: DeviceSession, raw_message: str) -> None:
        try:
            payload = json.loads(raw_message)
        except json.JSONDecodeError:
            await self._reject_connection(session, "invalid_json", "Invalid JSON payload", 1003)

        if not isinstance(payload, dict):
            await self._reject_connection(session, "invalid_payload", "Message payload must be an object", 1003)

        message_type = str(payload.get("type", "")).strip().lower()
        handler = self._handlers.get(message_type)
        if handler is None:
            await self._reject_connection(session, "unsupported_type", "Unsupported message type", 1003)

        await handler(session, payload)

    # Procesa la autenticacion del dispositivo.
    async def _handle_auth(self, session: DeviceSession, payload: dict[str, object]) -> None:
        message = await self._parse_message(
            session,
            AuthMessage,
            payload,
            "invalid_auth",
            "Invalid auth payload",
        )
        is_valid = await self.backend.validate_token(message.deviceId, message.token)
        if not is_valid:
            await self._reject_connection(session, "invalid_credentials", "Invalid device credentials", 4003)

        replaced_session = await self.registry.register_authenticated(session, message.deviceId)
        await self._send_json(
            session,
            {
                "type": "auth_ok",
                "deviceId": message.deviceId,
                "timestamp": utc_now().isoformat(),
            },
        )
        self._spawn_task(self.backend.notify_authenticated(message.deviceId, utc_now()))

        if replaced_session is not None:
            await self._try_close(replaced_session.websocket, 4000, "Session replaced")

    # Procesa la telemetria biometrica del dispositivo.
    async def _handle_telemetry(self, session: DeviceSession, payload: dict[str, object]) -> None:
        message = await self._parse_message(
            session,
            TelemetryMessage,
            payload,
            "invalid_telemetry",
            "Invalid telemetry payload",
        )
        await self._ensure_authenticated(session, message.deviceId)

        snapshot = TelemetrySnapshot(
            device_id=message.deviceId,
            bpm=message.bpm,
            spo2=message.spo2,
            sensor_connected=message.sensorConnected,
            device_timestamp=message.timestamp,
            received_at=utc_now(),
        )
        prediction = await self.predictor.evaluate(snapshot)
        snapshot.prediction_state = prediction.state.value
        snapshot.prediction_confidence = prediction.confidence
        snapshot.prediction_reasoning = prediction.reasoning
        await self.registry.store_telemetry(session, snapshot)
        self._spawn_task(self.backend.forward_telemetry(snapshot))

    # Procesa un heartbeat del ESP32.
    async def _handle_heartbeat(self, session: DeviceSession, payload: dict[str, object]) -> None:
        message = await self._parse_message(
            session,
            HeartbeatMessage,
            payload,
            "invalid_heartbeat",
            "Invalid heartbeat payload",
        )
        await self._ensure_authenticated(session, message.deviceId)
        await self.registry.touch_activity(session, heartbeat=True)

    # Procesa el ACK de un comando enviado al dispositivo.
    async def _handle_ack(self, session: DeviceSession, payload: dict[str, object]) -> None:
        message = await self._parse_message(
            session,
            AckMessage,
            payload,
            "invalid_ack",
            "Invalid ack payload",
        )
        await self._ensure_authenticated(session, message.deviceId)
        await self.registry.touch_activity(session)
        self._spawn_task(self.backend.notify_command_acknowledged(message))

    # Cierra la conexion si el mensaje requiere una sesion autenticada.
    async def _ensure_authenticated(self, session: DeviceSession, device_id: str) -> None:
        if not session.authenticated or session.device_id is None:
            await self._reject_connection(session, "unauthenticated", "Authenticate first", 4004)

        if session.device_id != device_id:
            await self._reject_connection(session, "device_mismatch", "Device ID does not match the session", 4004)

    # Cierra la conexion con un error controlado.
    async def _reject_connection(
        self,
        session: DeviceSession,
        code: str,
        message: str,
        close_code: int,
    ) -> None:
        await self._send_json(session, ErrorMessage(code=code, message=message).model_dump())
        await self._try_close(session.websocket, close_code, message)
        raise ConnectionClosed(code)

    # Valida payloads y responde un error consistente al dispositivo.
    async def _parse_message(
        self,
        session: DeviceSession,
        model_class,
        payload: dict[str, object],
        error_code: str,
        error_message: str,
    ):
        try:
            return model_class.model_validate(payload)
        except ValidationError:
            await self._reject_connection(session, error_code, error_message, 1003)

    # Limpia la sesion y notifica desconexiones una sola vez.
    async def _cleanup_session(self, session: DeviceSession, reason: str) -> None:
        if session.device_id is None:
            return

        was_active = await self.registry.unregister_if_active(session, reason)
        if was_active:
            await self.predictor.clear_device(session.device_id)
            self._spawn_task(self.backend.notify_disconnected(session.device_id, reason, utc_now()))

    # Supervisa timeouts de inactividad del canal realtime.
    async def _monitor_timeouts(self) -> None:
        while not self._stop_event.is_set():
            stale_sessions = await self.registry.get_stale_sessions(self.settings.heartbeat_timeout_seconds)
            for session in stale_sessions:
                await self._try_close(session.websocket, 4001, "Heartbeat timeout")
                await self._cleanup_session(session, "heartbeat_timeout")

            try:
                await asyncio.wait_for(
                    self._stop_event.wait(),
                    timeout=self.settings.heartbeat_check_interval_seconds,
                )
            except asyncio.TimeoutError:
                continue

    # Serializa una telemetria para las respuestas REST.
    def _build_telemetry_response(
        self,
        snapshot: TelemetrySnapshot | None,
    ) -> TelemetrySnapshotResponse | None:
        if snapshot is None:
            return None

        return TelemetrySnapshotResponse(
            deviceId=snapshot.device_id,
            bpm=snapshot.bpm,
            spo2=snapshot.spo2,
            sensorConnected=snapshot.sensor_connected,
            deviceTimestamp=snapshot.device_timestamp,
            receivedAt=snapshot.received_at,
            predictionState=snapshot.prediction_state,
            predictionConfidence=snapshot.prediction_confidence,
            predictionReasoning=snapshot.prediction_reasoning,
        )

    # Envia JSON de forma segura por la sesion activa.
    async def _send_json(self, session: DeviceSession, payload: dict[str, object]) -> None:
        if session.websocket.application_state == WebSocketState.DISCONNECTED:
            return

        async with session.send_lock:
            await session.websocket.send_json(payload)

    # Intenta cerrar un socket sin lanzar ruido extra.
    async def _try_close(self, websocket: WebSocket, code: int, reason: str) -> None:
        if websocket.application_state == WebSocketState.DISCONNECTED:
            return

        try:
            await websocket.close(code=code, reason=reason)
        except RuntimeError:
            pass

    # Resuelve una razon legible a partir del close code.
    def _resolve_disconnect_reason(self, code: int) -> str:
        if code == 4000:
            return "session_replaced"
        if code == 4001:
            return "heartbeat_timeout"
        if code == 4003:
            return "invalid_credentials"
        if code == 4004:
            return "protocol_violation"
        if code == 1000:
            return "normal_closure"
        return "client_disconnected"

    # Ejecuta tareas auxiliares sin bloquear el canal realtime.
    def _spawn_task(self, coroutine) -> None:
        task = asyncio.create_task(coroutine)
        self._background_tasks.add(task)
        task.add_done_callback(lambda completed: self._background_tasks.discard(completed))
