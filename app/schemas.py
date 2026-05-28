from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


# Define el mensaje de autenticacion del ESP32.
class AuthMessage(BaseModel):
    type: Literal["auth"]
    deviceId: str = Field(min_length=1)
    token: str = Field(min_length=1)


# Define la telemetria biometrica enviada por el dispositivo.
class TelemetryMessage(BaseModel):
    type: Literal["telemetry"]
    deviceId: str = Field(min_length=1)
    timestamp: int = Field(gt=0)
    bpm: float = Field(ge=25, le=250)
    spo2: float = Field(ge=50, le=100)
    sensorConnected: bool


# Define el heartbeat periodico del dispositivo.
class HeartbeatMessage(BaseModel):
    type: Literal["heartbeat"]
    deviceId: str = Field(min_length=1)
    timestamp: int = Field(gt=0)


# Define el ACK de comandos enviado por el ESP32.
class AckMessage(BaseModel):
    type: Literal["ack"]
    deviceId: str = Field(min_length=1)
    commandId: str = Field(min_length=1)
    status: str = Field(min_length=1)


# Modela el payload de luz enviado al dispositivo.
class CommandPayload(BaseModel):
    color: str = Field(pattern=r"^#[0-9A-Fa-f]{6}$")
    intensity: int = Field(ge=0, le=100)
    mode: Literal["calm", "steady", "pulse"]


# Representa un comando listo para enviarse al ESP32.
class DeviceCommandMessage(BaseModel):
    type: Literal["command"] = "command"
    commandId: str
    action: Literal["set_light"] = "set_light"
    payload: CommandPayload


# Valida el comando REST de luces.
class LightCommandRequest(BaseModel):
    color: str = Field(pattern=r"^#[0-9A-Fa-f]{6}$")
    intensity: int = Field(ge=0, le=100)
    mode: Literal["calm", "steady", "pulse"]


# Expone errores del canal WebSocket.
class ErrorMessage(BaseModel):
    type: Literal["error"] = "error"
    code: str
    message: str


# Resume el estado corto de un dispositivo conectado.
class ConnectedDeviceResponse(BaseModel):
    deviceId: str
    state: str
    connectedAt: datetime | None
    lastActivityAt: datetime | None
    lastHeartbeatAt: datetime | None


# Expone el estado detallado del dispositivo.
class DeviceStatusResponse(BaseModel):
    deviceId: str
    state: str
    connected: bool
    connectedAt: datetime | None
    lastActivityAt: datetime | None
    lastHeartbeatAt: datetime | None
    disconnectedAt: datetime | None
    disconnectReason: str | None


# Expone una muestra de telemetria serializable.
class TelemetrySnapshotResponse(BaseModel):
    deviceId: str
    bpm: float
    spo2: float
    sensorConnected: bool
    deviceTimestamp: int
    receivedAt: datetime
    predictionState: str
    predictionConfidence: float
    predictionReasoning: str


# Expone la telemetria mas reciente y un historial corto.
class LatestTelemetryResponse(BaseModel):
    deviceId: str
    latestTelemetry: TelemetrySnapshotResponse | None
    recentHistory: list[TelemetrySnapshotResponse]


# Resume el resultado del despacho de un comando.
class CommandDispatchResponse(BaseModel):
    deviceId: str
    commandId: str
    action: str
    status: str
