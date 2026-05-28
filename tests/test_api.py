from __future__ import annotations

from fastapi.testclient import TestClient

from app.config import FallbackSettings, PredictionSettings, Settings
from app.main import create_app


# Construye una configuracion estable para pruebas.
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


# Verifica el endpoint de contingencia.
def test_fallback_config_endpoint() -> None:
    with TestClient(create_app(build_settings())) as client:
        response = client.get("/api/devices/fallback-config")

    assert response.status_code == 200
    assert response.json() == {
        "ledColor": "#4A90E2",
        "ledIntensity": 60,
        "ledMode": "calm",
        "heartbeatIntervalSeconds": 10,
        "description": "Calm mode - backend unavailable",
    }


# Verifica el flujo REST de comando sobre una sesion autenticada.
def test_command_dispatch_to_connected_device() -> None:
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

            response = client.post(
                "/api/devices/ESP32_001/commands/light",
                json={
                    "color": "#4A90E2",
                    "intensity": 60,
                    "mode": "calm",
                },
            )

            command_message = websocket.receive_json()

    assert response.status_code == 200
    assert response.json()["status"] == "sent"
    assert command_message["type"] == "command"
    assert command_message["action"] == "set_light"
    assert command_message["payload"]["mode"] == "calm"
