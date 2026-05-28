from __future__ import annotations

import json
import os
from dataclasses import dataclass


# Convierte un valor de entorno a entero con fallback seguro.
def _parse_int(raw_value: str | None, default: int) -> int:
    try:
        return int(raw_value if raw_value is not None else default)
    except (TypeError, ValueError):
        return default


# Convierte un valor de entorno a flotante con fallback seguro.
def _parse_float(raw_value: str | None, default: float) -> float:
    try:
        return float(raw_value if raw_value is not None else default)
    except (TypeError, ValueError):
        return default


# Convierte flags de entorno a booleanos simples.
def _parse_bool(raw_value: str | None, default: bool) -> bool:
    if raw_value is None or raw_value.strip() == "":
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


# Convierte la lista de origenes desde variables de entorno.
def _parse_allowed_origins(raw_value: str | None) -> list[str]:
    if raw_value is None or raw_value.strip() == "":
        return ["*"]

    if raw_value.strip() == "*":
        return ["*"]

    origins = [item.strip() for item in raw_value.split(",") if item.strip()]
    return origins or ["*"]


# Carga tokens locales desde JSON o variables DEVICE_TOKEN_*.
def _load_device_tokens() -> dict[str, str]:
    tokens: dict[str, str] = {}
    raw_json = os.getenv("DEVICE_TOKENS_JSON", "").strip()

    if raw_json:
        try:
            parsed = json.loads(raw_json)
        except json.JSONDecodeError:
            parsed = {}

        if isinstance(parsed, dict):
            for key, value in parsed.items():
                key_text = str(key).strip()
                value_text = str(value).strip()
                if key_text and value_text:
                    tokens[key_text] = value_text

    for key, value in os.environ.items():
        if not key.startswith("DEVICE_TOKEN_"):
            continue
        value_text = value.strip()
        if not value_text:
            continue
        device_id = key.removeprefix("DEVICE_TOKEN_").strip()
        if device_id:
            tokens[device_id] = value_text

    return tokens


@dataclass(slots=True)
# Agrupa la configuracion autonoma de contingencia.
class FallbackSettings:
    led_color: str
    led_intensity: int
    led_mode: str
    heartbeat_interval_seconds: int
    description: str


@dataclass(slots=True)
# Agrupa la configuracion del predictor clinico.
class PredictionSettings:
    gemini_api_key: str
    gemini_model: str
    gemini_enabled: bool
    prediction_window_seconds: int
    prediction_interval_seconds: int
    prediction_min_samples: int
    warning_bpm_trend_threshold: float
    warning_spo2_trend_threshold: float


@dataclass(slots=True)
# Centraliza la configuracion externa del servicio.
class Settings:
    app_name: str
    host: str
    port: int
    ws_path: str
    allowed_origins: list[str]
    heartbeat_timeout_seconds: int
    heartbeat_check_interval_seconds: int
    telemetry_history_limit: int
    backend_base_url: str
    internal_token: str
    integration_timeout_ms: int
    device_tokens: dict[str, str]
    fallback: FallbackSettings
    prediction: PredictionSettings

    # Carga la configuracion usando variables de entorno.
    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            app_name=os.getenv("APP_NAME", "neurolive-realtime-py"),
            host=os.getenv("HOST", "0.0.0.0"),
            port=_parse_int(os.getenv("PORT"), 8080),
            ws_path=os.getenv("WS_ENDPOINT", "/ws/device"),
            allowed_origins=_parse_allowed_origins(os.getenv("WS_ALLOWED_ORIGINS", "*")),
            heartbeat_timeout_seconds=_parse_int(os.getenv("HEARTBEAT_TIMEOUT_SECONDS"), 15),
            heartbeat_check_interval_seconds=_parse_int(os.getenv("HEARTBEAT_CHECK_INTERVAL_SECONDS"), 5),
            telemetry_history_limit=_parse_int(os.getenv("TELEMETRY_HISTORY_LIMIT"), 20),
            backend_base_url=os.getenv("BACKEND_BASE_URL", "https://neurolive-backend.azurewebsites.net").strip(),
            internal_token=os.getenv("INTERNAL_TOKEN", "ws-internal-secret-change-in-prod").strip(),
            integration_timeout_ms=_parse_int(os.getenv("INTEGRATION_TIMEOUT_MS"), 3000),
            device_tokens=_load_device_tokens(),
            fallback=FallbackSettings(
                led_color=os.getenv("FALLBACK_LED_COLOR", "#4A90E2").strip() or "#4A90E2",
                led_intensity=_parse_int(os.getenv("FALLBACK_LED_INTENSITY"), 60),
                led_mode=os.getenv("FALLBACK_LED_MODE", "calm").strip() or "calm",
                heartbeat_interval_seconds=_parse_int(os.getenv("FALLBACK_HEARTBEAT_INTERVAL"), 10),
                description=os.getenv(
                    "FALLBACK_DESCRIPTION",
                    "Calm mode - backend unavailable",
                ).strip()
                or "Calm mode - backend unavailable",
            ),
            prediction=PredictionSettings(
                gemini_api_key=os.getenv("GEMINI_API_KEY", "").strip(),
                gemini_model=os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite").strip()
                or "gemini-2.5-flash-lite",
                gemini_enabled=_parse_bool(os.getenv("GEMINI_ENABLED"), True),
                prediction_window_seconds=_parse_int(os.getenv("PREDICTION_WINDOW_SECONDS"), 30),
                prediction_interval_seconds=_parse_int(os.getenv("PREDICTION_INTERVAL_SECONDS"), 20),
                prediction_min_samples=_parse_int(os.getenv("PREDICTION_MIN_SAMPLES"), 8),
                warning_bpm_trend_threshold=_parse_float(os.getenv("WARNING_BPM_TREND"), 12.0),
                warning_spo2_trend_threshold=_parse_float(os.getenv("WARNING_SPO2_TREND"), 2.0),
            ),
        )
