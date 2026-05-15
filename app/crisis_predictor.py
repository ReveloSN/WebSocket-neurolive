from __future__ import annotations

import asyncio
import json
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum

from app.config import PredictionSettings
from app.sliding_window import SlidingWindowBuffer, TelemetryWindowSummary
from app.state import TelemetrySnapshot


LOGGER = logging.getLogger("neurolive.realtime.predictor")


class PredictionState(str, Enum):
    STABLE = "STABLE"
    WARNING = "WARNING"
    PRE_CRISIS = "PRE_CRISIS"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"


@dataclass(slots=True)
# Resultado compacto que viaja con la telemetria.
class PredictionResult:
    state: PredictionState
    confidence: float
    reasoning: str


# Predice riesgo temprano sin bloquear el flujo del ESP32.
class CrisisPredictor:
    def __init__(self, settings: PredictionSettings) -> None:
        # Mantiene estado predictivo en memoria por dispositivo.
        self._settings = settings
        self._window = SlidingWindowBuffer(settings.prediction_window_seconds)
        self._last_prediction: dict[str, PredictionResult] = {}
        self._last_gemini_call: dict[str, datetime] = {}
        self._running_tasks: set[asyncio.Task[None]] = set()
        self._enabled = settings.gemini_enabled and bool(settings.gemini_api_key)
        self._model = None

        if self._enabled:
            self._configure_gemini()
        else:
            LOGGER.info("AI predictor disabled; GEMINI_API_KEY is empty or GEMINI_ENABLED is false")

    # Evalua una muestra y programa Gemini solo cuando conviene.
    async def evaluate(self, snapshot: TelemetrySnapshot) -> PredictionResult:
        try:
            summary = await self._window.add_sample(snapshot)
            local_result = self._evaluate_locally(summary)
            cached_result = self._last_prediction.get(snapshot.device_id)
            result = self._most_severe(local_result, cached_result)
            self._last_prediction[snapshot.device_id] = result

            if self._should_call_gemini(summary, local_result):
                self._schedule_gemini_refresh(summary)

            return result
        except Exception as exception:
            LOGGER.warning("Prediction fallback used deviceId=%s reason=%s", snapshot.device_id, exception)
            return PredictionResult(PredictionState.STABLE, 0.1, "Prediction fallback after local error")

    # Limpia memoria predictiva de un dispositivo desconectado.
    async def clear_device(self, device_id: str) -> None:
        await self._window.clear_device(device_id)
        self._last_prediction.pop(device_id, None)
        self._last_gemini_call.pop(device_id, None)

    # Cancela tareas pendientes al detener el servicio.
    async def close(self) -> None:
        if not self._running_tasks:
            return
        for task in list(self._running_tasks):
            task.cancel()
        await asyncio.gather(*self._running_tasks, return_exceptions=True)

    # Inicializa Gemini si esta disponible.
    def _configure_gemini(self) -> None:
        try:
            import google.generativeai as genai

            genai.configure(api_key=self._settings.gemini_api_key)
            self._model = genai.GenerativeModel(self._settings.gemini_model)
            LOGGER.info("AI predictor enabled model=%s", self._settings.gemini_model)
        except Exception as exception:
            self._enabled = False
            LOGGER.warning("AI predictor disabled after Gemini setup failure: %s", exception)

    # Aplica reglas rapidas antes de cualquier llamada externa.
    def _evaluate_locally(self, summary: TelemetryWindowSummary) -> PredictionResult:
        if summary.sample_count < self._settings.prediction_min_samples:
            return PredictionResult(
                PredictionState.INSUFFICIENT_DATA,
                0.2,
                f"Only {summary.sample_count} samples available",
            )

        bpm_rising = summary.bpm_trend >= self._settings.warning_bpm_trend_threshold
        spo2_falling = summary.spo2_trend >= self._settings.warning_spo2_trend_threshold
        weak_sensor = summary.sensor_connected_ratio < 0.8

        if bpm_rising and spo2_falling:
            return PredictionResult(
                PredictionState.PRE_CRISIS,
                0.78,
                "BPM rising while SpO2 is dropping in the recent window",
            )
        if bpm_rising or spo2_falling or weak_sensor:
            return PredictionResult(
                PredictionState.WARNING,
                0.58,
                "Recent biometric trend needs attention",
            )
        return PredictionResult(PredictionState.STABLE, 0.72, "Recent window remains stable")

    # Decide si Gemini merece ejecutarse para esta ventana.
    def _should_call_gemini(self, summary: TelemetryWindowSummary, local_result: PredictionResult) -> bool:
        if not self._enabled or self._model is None:
            return False
        if local_result.state in {PredictionState.STABLE, PredictionState.INSUFFICIENT_DATA}:
            return False
        now = datetime.now(timezone.utc)
        last_call = self._last_gemini_call.get(summary.device_id)
        if last_call is not None and now - last_call < timedelta(seconds=self._settings.prediction_interval_seconds):
            return False
        self._last_gemini_call[summary.device_id] = now
        return True

    # Ejecuta Gemini en segundo plano para no frenar el socket.
    def _schedule_gemini_refresh(self, summary: TelemetryWindowSummary) -> None:
        task = asyncio.create_task(self._refresh_with_gemini(summary))
        self._running_tasks.add(task)
        task.add_done_callback(lambda completed: self._running_tasks.discard(completed))

    # Refina la prediccion usando Gemini con salida JSON.
    async def _refresh_with_gemini(self, summary: TelemetryWindowSummary) -> None:
        try:
            response_text = await asyncio.wait_for(
                asyncio.to_thread(self._call_gemini, summary),
                timeout=4.0,
            )
            result = self._parse_gemini_response(response_text)
            current = self._last_prediction.get(summary.device_id)
            self._last_prediction[summary.device_id] = self._most_severe(result, current)
            LOGGER.info(
                "AI prediction refreshed deviceId=%s state=%s confidence=%.2f",
                summary.device_id,
                result.state,
                result.confidence,
            )
        except Exception as exception:
            LOGGER.warning("AI prediction fallback deviceId=%s reason=%s", summary.device_id, exception)

    # Llama al modelo con un prompt minimo y sin datos personales.
    def _call_gemini(self, summary: TelemetryWindowSummary) -> str:
        prompt = (
            "Return only JSON with keys predictionState, predictionConfidence, predictionReasoning. "
            "Allowed predictionState values: STABLE, WARNING, PRE_CRISIS. "
            "Use only these anonymized recent telemetry stats: "
            f"samples={summary.sample_count}, durationSeconds={summary.duration_seconds:.1f}, "
            f"bpmStart={summary.first_bpm:.1f}, bpmEnd={summary.last_bpm:.1f}, "
            f"bpmAverage={summary.average_bpm:.1f}, bpmTrend={summary.bpm_trend:.1f}, "
            f"spo2Start={summary.first_spo2:.1f}, spo2End={summary.last_spo2:.1f}, "
            f"spo2Average={summary.average_spo2:.1f}, spo2Drop={summary.spo2_trend:.1f}, "
            f"sensorConnectedRatio={summary.sensor_connected_ratio:.2f}."
        )
        response = self._model.generate_content(
            prompt,
            generation_config={"temperature": 0.1, "max_output_tokens": 120},
        )
        return getattr(response, "text", "") or ""

    # Convierte la respuesta del modelo a una estructura segura.
    def _parse_gemini_response(self, response_text: str) -> PredictionResult:
        cleaned = response_text.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        data = json.loads(cleaned)
        state = PredictionState(str(data.get("predictionState", "WARNING")).strip().upper())
        confidence = self._clamp_float(data.get("predictionConfidence"), 0.55)
        reasoning = str(data.get("predictionReasoning", "AI prediction available")).strip()
        return PredictionResult(state, confidence, reasoning[:220])

    # Compara dos predicciones y conserva la mas severa.
    def _most_severe(
        self,
        left: PredictionResult,
        right: PredictionResult | None,
    ) -> PredictionResult:
        if right is None:
            return left
        if self._severity(right.state) > self._severity(left.state):
            return right
        return left if left.confidence >= right.confidence else right

    # Ordena estados predictivos por severidad.
    def _severity(self, state: PredictionState) -> int:
        return {
            PredictionState.INSUFFICIENT_DATA: 0,
            PredictionState.STABLE: 1,
            PredictionState.WARNING: 2,
            PredictionState.PRE_CRISIS: 3,
        }.get(state, 0)

    # Normaliza confianza a 0..1.
    def _clamp_float(self, value: object, default: float) -> float:
        try:
            parsed = float(value)
        except (TypeError, ValueError):
            return default
        return max(0.0, min(parsed, 1.0))
