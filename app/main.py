from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import Settings
from app.schemas import (
    CommandDispatchResponse,
    ConnectedDeviceResponse,
    DeviceStatusResponse,
    LatestTelemetryResponse,
    LightCommandRequest,
)
from app.service import RealtimeGatewayService


# Construye la aplicacion FastAPI del gateway realtime.
def create_app(settings: Settings | None = None) -> FastAPI:
    runtime_settings = settings or Settings.from_env()
    _configure_logging()
    gateway_service = RealtimeGatewayService(runtime_settings)

    @asynccontextmanager
    # Administra el ciclo de vida del servicio asincrono.
    async def lifespan(_: FastAPI):
        await gateway_service.start()
        yield
        await gateway_service.stop()

    app = FastAPI(
        title="Neuro Live Realtime Service",
        version="2.0.0",
        lifespan=lifespan,
    )
    _configure_cors(app, runtime_settings)

    # Expone un resumen rapido del servicio.
    @app.get("/")
    async def root() -> dict[str, object]:
        return {
            "service": runtime_settings.app_name,
            "status": "running",
            "wsPath": runtime_settings.ws_path,
        }

    # Expone una ruta simple de health para Railway.
    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    # Mantiene compatibilidad con el health antiguo estilo Spring.
    @app.get("/actuator/health")
    async def actuator_health() -> dict[str, str]:
        return {"status": "UP"}

    # Lista dispositivos conectados por REST.
    @app.get("/api/devices/connected", response_model=list[ConnectedDeviceResponse])
    async def get_connected_devices() -> list[ConnectedDeviceResponse]:
        return await gateway_service.get_connected_devices()

    # Retorna el estado detallado del dispositivo.
    @app.get("/api/devices/{device_id}/status", response_model=DeviceStatusResponse)
    async def get_device_status(device_id: str) -> DeviceStatusResponse:
        return await gateway_service.get_device_status(device_id)

    # Retorna la telemetria mas reciente del dispositivo.
    @app.get("/api/devices/{device_id}/telemetry/latest", response_model=LatestTelemetryResponse)
    async def get_latest_telemetry(device_id: str) -> LatestTelemetryResponse:
        return await gateway_service.get_latest_telemetry(device_id)

    # Envia un comando de luz al ESP32 conectado.
    @app.post("/api/devices/{device_id}/commands/light", response_model=CommandDispatchResponse)
    async def send_light_command(
        device_id: str,
        request: LightCommandRequest,
    ) -> CommandDispatchResponse:
        return await gateway_service.send_light_command(device_id, request)

    # Expone la configuracion autonoma de contingencia.
    @app.get("/api/devices/fallback-config")
    async def get_fallback_config() -> dict[str, object]:
        return gateway_service.get_fallback_config()

    app.add_api_websocket_route(runtime_settings.ws_path, gateway_service.handle_connection)
    return app


# Configura el logging basico del servicio.
def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


# Configura CORS para pruebas y herramientas externas.
def _configure_cors(app: FastAPI, settings: Settings) -> None:
    allow_credentials = settings.allowed_origins != ["*"]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=allow_credentials,
        allow_methods=["*"],
        allow_headers=["*"],
    )


app = create_app()
