from __future__ import annotations

import asyncio
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timedelta

from app.state import TelemetrySnapshot


@dataclass(slots=True)
# Resume una ventana reciente sin exponer datos personales.
class TelemetryWindowSummary:
    device_id: str
    sample_count: int
    duration_seconds: float
    first_bpm: float
    last_bpm: float
    average_bpm: float
    bpm_trend: float
    first_spo2: float
    last_spo2: float
    average_spo2: float
    spo2_drop: float
    sensor_connected_ratio: float


# Mantiene ventanas recientes de telemetria por dispositivo.
class SlidingWindowBuffer:
    def __init__(self, window_seconds: int, max_samples_per_device: int = 240) -> None:
        # Protege buffers compartidos entre conexiones y tareas.
        self._lock = asyncio.Lock()
        self._window_seconds = max(window_seconds, 1)
        self._max_samples_per_device = max(max_samples_per_device, 1)
        self._buffers: dict[str, deque[TelemetrySnapshot]] = {}

    # Agrega una muestra y limpia datos viejos.
    async def add_sample(self, snapshot: TelemetrySnapshot) -> TelemetryWindowSummary:
        async with self._lock:
            buffer = self._buffers.setdefault(snapshot.device_id, deque(maxlen=self._max_samples_per_device))
            buffer.append(snapshot)
            self._evict_old(buffer, snapshot.received_at)
            return self._summarize(snapshot.device_id, buffer)

    # Borra la memoria asociada a un dispositivo desconectado.
    async def clear_device(self, device_id: str) -> None:
        async with self._lock:
            self._buffers.pop(device_id, None)

    # Elimina muestras fuera del rango temporal.
    def _evict_old(self, buffer: deque[TelemetrySnapshot], reference_time: datetime) -> None:
        threshold = reference_time - timedelta(seconds=self._window_seconds)
        while buffer and buffer[0].received_at < threshold:
            buffer.popleft()

    # Calcula estadisticas compactas de la ventana.
    def _summarize(self, device_id: str, buffer: deque[TelemetrySnapshot]) -> TelemetryWindowSummary:
        ordered = list(buffer)
        first = ordered[0]
        last = ordered[-1]
        sample_count = len(ordered)
        duration_seconds = max((last.received_at - first.received_at).total_seconds(), 0.0)
        bpm_values = [sample.bpm for sample in ordered]
        spo2_values = [sample.spo2 for sample in ordered]
        sensor_connected_count = sum(1 for sample in ordered if sample.sensor_connected)

        return TelemetryWindowSummary(
            device_id=device_id,
            sample_count=sample_count,
            duration_seconds=duration_seconds,
            first_bpm=first.bpm,
            last_bpm=last.bpm,
            average_bpm=sum(bpm_values) / sample_count,
            bpm_trend=last.bpm - first.bpm,
            first_spo2=first.spo2,
            last_spo2=last.spo2,
            average_spo2=sum(spo2_values) / sample_count,
            spo2_drop=first.spo2 - last.spo2,
            sensor_connected_ratio=sensor_connected_count / sample_count,
        )
