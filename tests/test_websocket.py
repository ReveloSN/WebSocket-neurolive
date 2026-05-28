from __future__ import annotations

import asyncio

import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from app.backend import BackendClient
from app.config import FallbackSettings, PredictionSettings, Settings
from app.main import create_app
from app.state import TelemetrySnapshot, utc_now


# Construye una configuracion local para pruebas del canal.
def build_settings() -> Settings:
    return Settings(
        app_name="neurolive-realtime-py-test",
        host="127.0.0.1",
        port=8080,
        ws_path="/ws/device",
        allowed_origins=["*"],
        heartbeat_timeout_seconds=15,
        heartbeat_check_interval_seconds=30,
        telemetry_history_limit=20,
        backend_base_url="",
        internal_token="test-internal-token",
        integration_timeout_ms=500,
        device_tokens={"ESP32_001": "abc123"},
        fallback=FallbackSettings(
            led_color="#4A90E2",
            led_intensity=60,
            led_mode="calm",
            heartbeat_interval_seconds=10,
            description="Calm mode - backend unavailable",
        ),
        prediction=PredictionSettings(
            gemini_api_key="",
            gemini_model="gemini-2.5-flash-lite",
            gemini_enabled=False,
            prediction_window_seconds=30,
            prediction_interval_seconds=20,
            prediction_min_samples=3,
            warning_bpm_trend_threshold=12.0,
            warning_spo2_trend_threshold=2.0,
        ),
    )


# Verifica autenticacion y persistencia de telemetria.
def test_auth_and_telemetry_flow() -> None:
    with TestClient(create_app(build_settings())) as client:
        with client.websocket_connect("/ws/device") as websocket:
            websocket.send_json(
                {
                    "type": "auth",
                    "deviceId": "ESP32_001",
                    "token": "abc123",
                }
            )
            auth_response = websocket.receive_json()
            assert auth_response["type"] == "auth_ok"

            websocket.send_json(
                {
                    "type": "telemetry",
                    "deviceId": "ESP32_001",
                    "timestamp": 1710000000,
                    "bpm": 92,
                    "spo2": 98,
                    "sensorConnected": True,
                }
            )

            websocket.send_json(
                {
                    "type": "heartbeat",
                    "deviceId": "ESP32_001",
                    "timestamp": 1710000001,
                }
            )

            status_response = client.get("/api/devices/ESP32_001/status")
            telemetry_response = client.get("/api/devices/ESP32_001/telemetry/latest")

    assert status_response.status_code == 200
    assert status_response.json()["connected"] is True
    assert telemetry_response.status_code == 200
    assert telemetry_response.json()["latestTelemetry"]["bpm"] == 92.0
    assert telemetry_response.json()["latestTelemetry"]["predictionState"] == "INSUFFICIENT_DATA"
    assert telemetry_response.json()["recentHistory"][0]["deviceId"] == "ESP32_001"


# Verifica que el payload interno conserva los campos predictivos.
def test_backend_forwarding_includes_prediction_fields(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = build_settings()
    settings.backend_base_url = "https://backend.example"
    backend = BackendClient(settings)
    captured: dict[str, object] = {}

    async def fake_post_safely(path: str, payload: dict[str, object], context: str) -> None:
        captured["path"] = path
        captured["payload"] = payload
        captured["context"] = context

    monkeypatch.setattr(backend, "_post_safely", fake_post_safely)
    backend._client = object()  # type: ignore[assignment]

    snapshot = TelemetrySnapshot(
        device_id="ESP32_001",
        bpm=101,
        spo2=94,
        sensor_connected=True,
        device_timestamp=1710000002,
        received_at=utc_now(),
        prediction_state="WARNING",
        prediction_confidence=0.58,
        prediction_reasoning="Recent biometric trend needs attention",
    )

    asyncio.run(backend.forward_telemetry(snapshot))

    payload = captured["payload"]
    assert captured["path"] == "/internal/telemetry"
    assert isinstance(payload, dict)
    assert payload["predictionState"] == "WARNING"
    assert payload["predictionConfidence"] == 0.58
    assert payload["predictionReasoning"] == "Recent biometric trend needs attention"


# Verifica que sin auth la conexion se cierre con error.
def test_telemetry_without_auth_closes_connection() -> None:
    with TestClient(create_app(build_settings())) as client:
        with client.websocket_connect("/ws/device") as websocket:
            websocket.send_json(
                {
                    "type": "telemetry",
                    "deviceId": "ESP32_001",
                    "timestamp": 1710000000,
                    "bpm": 92,
                    "spo2": 98,
                    "sensorConnected": True,
                }
            )

            error_message = websocket.receive_json()
            assert error_message["type"] == "error"
            assert error_message["code"] == "unauthenticated"

            with pytest.raises(WebSocketDisconnect):
                websocket.receive_text()


# Verifica que un payload invalido responda error antes de cerrar.
@pytest.mark.parametrize("bpm", [10, 251])
def test_invalid_telemetry_payload_returns_error_message(bpm: int) -> None:
    with TestClient(create_app(build_settings())) as client:
        with client.websocket_connect("/ws/device") as websocket:
            websocket.send_json(
                {
                    "type": "auth",
                    "deviceId": "ESP32_001",
                    "token": "abc123",
                }
            )
            auth_response = websocket.receive_json()
            assert auth_response["type"] == "auth_ok"

            websocket.send_json(
                {
                    "type": "telemetry",
                    "deviceId": "ESP32_001",
                    "timestamp": 1710000000,
                    "bpm": bpm,
                    "spo2": 98,
                    "sensorConnected": True,
                }
            )

            error_message = websocket.receive_json()
            assert error_message["type"] == "error"
            assert error_message["code"] == "invalid_telemetry"

            with pytest.raises(WebSocketDisconnect):
                websocket.receive_text()
