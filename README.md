# Neuro Live Realtime Service

This repository contains the **Python realtime gateway** for Neuro Live.

Its responsibility is to keep a low-latency communication channel between the ESP32 devices and the platform through raw WebSocket + JSON, while keeping the existing HTTP contract with the main backend.

## What This Service Does

- accepts ESP32 WebSocket connections on `/ws/device`
- authenticates devices with `deviceId + token`
- receives telemetry, heartbeat, and command acknowledgements
- stores active sessions and recent telemetry in memory
- detects inactivity timeouts and disconnects stale sessions
- exposes REST endpoints for device status, fallback config, and light commands
- forwards internal events to the main backend using `X-Internal-Token`

## What This Service Does Not Do

- it does not replace the main backend
- it does not persist domain data in a database
- it does not own consent, audit, or clinical rules
- it does not implement dashboards or frontend state delivery

The **main backend** remains responsible for:

- biometric consent
- audit and traceability
- persistence and analytics
- patient, caregiver, and doctor domain rules
- crisis logic and application workflows

## Architecture

```text
ESP32
  |
  | WebSocket + JSON
  v
neurolive-realtime-py
  |
  | HTTP internal calls with X-Internal-Token
  v
neuro-live-backend
```

The backend contract used by this gateway is:

- `POST /internal/telemetry`
- `POST /internal/devices/{id}/authenticated`
- `POST /internal/devices/{id}/disconnected`
- `POST /internal/devices/validate-token`

## Main Features

- FastAPI + asyncio runtime
- raw WebSocket device channel
- local token validation through environment variables
- optional remote token validation through the main backend
- non-blocking telemetry forwarding
- recent telemetry history kept in memory
- fallback configuration endpoint for offline device behavior
- Railway-ready Docker image

## Project Structure

```text
app/
  __init__.py
  backend.py
  config.py
  main.py
  schemas.py
  service.py
  state.py
tests/
  test_api.py
  test_websocket.py
Dockerfile
requirements.txt
requirements-dev.txt
websocket-test-examples.md
```

## Environment Variables

### Core runtime

- `APP_NAME` default `neurolive-realtime-py`
- `HOST` default `0.0.0.0`
- `PORT` default `8080`
- `WS_ENDPOINT` default `/ws/device`
- `WS_ALLOWED_ORIGINS` default `*`

### Device authentication

- `DEVICE_TOKEN_ESP32_001=abc123`
- `DEVICE_TOKEN_ESP32_002=def456`
- `DEVICE_TOKENS_JSON={"ESP32_003":"ghi789"}`

If local device tokens are configured, validation is done locally. If no local tokens are present, the service validates against the backend using:

- `BACKEND_BASE_URL`
- `INTERNAL_TOKEN`

### Heartbeat and telemetry

- `HEARTBEAT_TIMEOUT_SECONDS` default `15`
- `HEARTBEAT_CHECK_INTERVAL_SECONDS` default `5`
- `TELEMETRY_HISTORY_LIMIT` default `20`

### Backend integration

- `BACKEND_BASE_URL` default `https://neurolive-backend.azurewebsites.net`
- `INTERNAL_TOKEN` default `ws-internal-secret-change-in-prod`
- `INTEGRATION_TIMEOUT_MS` default `3000`

### Fallback config

- `FALLBACK_LED_COLOR` default `#4A90E2`
- `FALLBACK_LED_INTENSITY` default `60`
- `FALLBACK_LED_MODE` default `calm`
- `FALLBACK_HEARTBEAT_INTERVAL` default `10`
- `FALLBACK_DESCRIPTION` default `Calm mode - backend unavailable`

## Running Locally

### 1. Create a virtual environment

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

### 2. Install dependencies

```powershell
python -m pip install -r requirements.txt
python -m pip install -r requirements-dev.txt
```

### 3. Start the service

```powershell
python -m uvicorn app.main:app --host 0.0.0.0 --port 8080
```

### 4. Useful local URLs

- `http://localhost:8080/health`
- `http://localhost:8080/actuator/health`
- `http://localhost:8080/docs`
- `ws://localhost:8080/ws/device`

## REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | Simple health response |
| `GET` | `/actuator/health` | Spring-compatible health response |
| `GET` | `/api/devices/connected` | Connected devices |
| `GET` | `/api/devices/{deviceId}/status` | Detailed device status |
| `GET` | `/api/devices/{deviceId}/telemetry/latest` | Latest telemetry and recent history |
| `POST` | `/api/devices/{deviceId}/commands/light` | Sends a light command |
| `GET` | `/api/devices/fallback-config` | Returns fallback hardware config |

## WebSocket Message Types

- `auth`
- `telemetry`
- `heartbeat`
- `ack`
- `command` (server to device)
- `error`

Detailed JSON examples are available in [websocket-test-examples.md](websocket-test-examples.md).

## Reconnection and Fallback Behavior

- every new WebSocket connection must authenticate again
- if the same device reconnects, the newest session replaces the previous one
- telemetry and heartbeat are accepted only after a successful `auth`
- if heartbeat or telemetry stops for too long, the session is closed and reported as disconnected
- the ESP32 should download `/api/devices/fallback-config` on boot and cache it locally
- if the backend or network becomes unavailable, the device should keep using the cached fallback intervention until connectivity returns

## Testing

Run the test suite with:

```powershell
python -m pytest
```

Current tests cover:

- fallback config endpoint
- auth and telemetry flow
- command dispatch flow
- rejection of telemetry without authentication
- rejection of invalid telemetry payloads

## Railway Deployment

This repository is ready to deploy with the included `Dockerfile`.

Recommended environment variables:

- `PORT=8080`
- `BACKEND_BASE_URL=https://neurolive-backend.azurewebsites.net`
- `INTERNAL_TOKEN=<shared-secret>`
- `WS_ALLOWED_ORIGINS=*`
- `DEVICE_TOKEN_ESP32_001=abc123`

Container start command:

```text
uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080}
```

## Notes

- the backend contract is language-agnostic and remains unchanged
- telemetry forwarding is fire-and-forget to avoid blocking the device loop
- this service is intentionally stateless beyond in-memory runtime data
