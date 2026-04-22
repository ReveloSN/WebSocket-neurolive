# Neuro Live Realtime Service

## Purpose

This repository contains the dedicated realtime communication service for **Neuro Live**.  
Its only responsibility is to handle low-latency, bidirectional communication between ESP32 devices and the platform through **raw WebSocket + JSON**, while staying ready to integrate with the existing main backend in a later phase.

This service does **not** replace the main backend.  
It complements it as a separate deployable module focused on realtime device connectivity.

## Why This Repository Was Separated

The main backend already handles the core business domain:

- user and patient management
- authentication and account flows
- device linking
- clinical and sensory regulation logic
- analytics and exports
- dashboards and frontend integration

This repository was separated because the project needs a specialized realtime channel for:

- persistent ESP32 connections
- continuous biometric telemetry
- low-latency command delivery
- heartbeat/disconnection detection
- session-oriented device communication

This matches the team architecture decision:

`ESP32 <-> Realtime WebSocket Service <-> Main Backend`

## Architecture Overview

```text
ESP32
  |
  |  raw WebSocket + JSON
  v
Neuro Live Realtime Service (this repo)
  |- device authentication
  |- session registry
  |- telemetry ingestion
  |- heartbeat timeout monitor
  |- command dispatch
  |- minimal REST endpoints
  |- future backend integration hooks
  v
Main Backend (existing, separate)
  |- consent and linking flows
  |- audit persistence
  |- core domain rules
  |- clinical logic
  |- dashboards and reports
```

The main backend remains responsible for **consent**, **audit**, and the **core domain rules** of Neuro Live.  
This realtime service stays focused on transport, sessions, telemetry, and device commands.

## Flow: ESP32 -> Realtime Service -> Main Backend

1. The ESP32 opens a WebSocket connection to `/ws/device`.
2. The device authenticates using `deviceId` and `token`.
3. Once authenticated, the device can send telemetry and heartbeat messages.
4. The service stores active session state, latest telemetry, and last activity in memory.
5. A REST client or future backend integration can send a light command to the device.
6. If the device stops sending telemetry or heartbeat for the configured timeout, the service marks it as disconnected.
7. Internal events are published so a future integration layer can forward telemetry, disconnections, and command acknowledgements to the main backend.

## Connection Loss, Reconnection, and Local Fallback

When the ESP32 loses connectivity with the realtime service, the expected behavior is:

1. The service marks the device as disconnected after the configured heartbeat timeout.
2. The ESP32 keeps a **basic local fallback behavior** so the hardware can remain minimally useful and safe without the server.
3. That fallback can mean keeping the last safe hardware state, switching to a simple local safe mode, or continuing minimal autonomous logic in firmware.
4. The ESP32 retries the WebSocket connection using a simple reconnect strategy such as delayed retries or exponential backoff.
5. After reconnecting, the ESP32 must send a new `auth` message before sending telemetry, heartbeat, or acknowledgements again.

Authentication is **session-based**, not permanent across reconnects.  
Every new WebSocket connection must authenticate again.

If a device reconnects with the same `deviceId`, the newest authenticated session becomes the active one and the older session is replaced.

## Scope Covered by This Repo

This repository directly covers the realtime communication portion of the general project:

- ESP32 connection handling
- basic device authentication
- biometric telemetry reception
- heartbeat and disconnection detection
- active session management
- light command delivery to hardware
- minimal REST endpoints for integration/testing
- future-ready integration hooks toward the main backend

It intentionally does **not** implement the full clinical domain logic.

## Functional and Non-Functional Support

### Functional requirements supported

- ESP32 device linking support at communication level
- biometric telemetry reception
- disconnection detection
- light actuator control
- visual wellbeing monitoring support
- realtime intervention triggering from external logic

### Non-functional requirements supported

- low latency through persistent WebSocket sessions
- non-blocking internal event propagation
- modular extensibility for future sensors/actions
- communication security through token-based device validation
- service availability as an isolated deployable component
- traceability support through clear logs and event publication
- future anonymization compatibility in higher layers
- autonomy of hardware preserved because the ESP32 protocol is isolated from backend domain logic

### Still owned by the main backend

- explicit biometric consent management
- audit persistence and traceability policies
- patient, caregiver, and doctor domain rules
- core domain decisions and clinical workflows
- account linking and authorization policies

## Tech Stack

- Java 17
- Spring Boot 3.x
- Maven
- Raw WebSocket
- JSON text messages
- REST for integration/testing
- Railway-ready configuration through environment variables

## Project Structure

```text
src/main/java/com/neurolive/realtime
|-- config
|-- controller
|-- dto
|-- exception
|-- model
|-- service
`-- websocket
    `-- processor
```

## Message Types

### Auth

```json
{
  "type": "auth",
  "deviceId": "ESP32_001",
  "token": "abc123"
}
```

### Telemetry

```json
{
  "type": "telemetry",
  "deviceId": "ESP32_001",
  "timestamp": 1710000000,
  "bpm": 92,
  "spo2": 98,
  "sensorConnected": true
}
```

### Heartbeat

```json
{
  "type": "heartbeat",
  "deviceId": "ESP32_001",
  "timestamp": 1710000001
}
```

### Ack

```json
{
  "type": "ack",
  "deviceId": "ESP32_001",
  "commandId": "cmd-001",
  "status": "applied"
}
```

### Command sent to ESP32

```json
{
  "type": "command",
  "commandId": "cmd-001",
  "action": "set_light",
  "payload": {
    "color": "#4A90E2",
    "intensity": 60,
    "mode": "calm"
  }
}
```

## REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/devices/connected` | Lists currently connected devices |
| `GET` | `/api/devices/{deviceId}/status` | Returns the current device status |
| `GET` | `/api/devices/{deviceId}/telemetry/latest` | Returns latest telemetry and recent history |
| `POST` | `/api/devices/{deviceId}/commands/light` | Sends a light command to the connected device |

### Light command request body

```json
{
  "color": "#4A90E2",
  "intensity": 60,
  "mode": "calm"
}
```

Allowed `mode` values:

- `calm`
- `steady`
- `pulse`

## Configuration

Main configuration lives in `src/main/resources/application.yml`.

Key environment variables:

- `PORT` default `8080`
- `WS_ENDPOINT` default `/ws/device`
- `WS_ALLOWED_ORIGINS` default `*`
- `DEVICE_TOKEN_ESP32_001` default `abc123`
- `DEVICE_TOKEN_ESP32_002` default `def456`
- `HEARTBEAT_TIMEOUT_SECONDS` default `15`
- `HEARTBEAT_CHECK_INTERVAL_MILLIS` default `5000`
- `TELEMETRY_HISTORY_LIMIT` default `20`
- `BACKEND_BASE_URL` optional placeholder for future backend integration

## Design Patterns Used

### Observer

Used through:

- `RealtimeEventPublisher`
- `RealtimeEventObserver`
- `LoggingRealtimeEventObserver`
- `BackendIntegrationObserver`

Why:

- telemetry, heartbeat, disconnect, auth, and ack events can be propagated without coupling the WebSocket layer directly to all consumers
- future integrations can subscribe to internal events without rewriting the handler flow

### Command

Used through:

- `DeviceCommand`
- `LightCommand`
- `DeviceCommandService`

Why:

- server-to-device actions are modeled as explicit command objects
- new hardware actions can be added later without changing the transport contract structure

### Factory Method

Used through:

- `MessageProcessorFactory`
- `AuthMessageProcessor`
- `TelemetryMessageProcessor`
- `HeartbeatMessageProcessor`
- `AckMessageProcessor`

Why:

- incoming WebSocket messages are routed by type to specialized processors
- this avoids a large fragile conditional block inside the main WebSocket handler

## Data Structures Used

### ConcurrentHashMap

Used for:

- `deviceId -> WebSocketSession`
- `deviceId -> latest telemetry`
- `deviceId -> last activity / heartbeat`
- `deviceId -> connection state`

Why:

- thread-safe access is required because multiple devices and scheduled tasks can operate concurrently

### Queue

Used through:

- `LinkedBlockingQueue` inside `RealtimeEventPublisher`

Why:

- internal events are dispatched asynchronously
- telemetry reception stays decoupled from downstream observers

### ArrayDeque

Used through:

- recent telemetry history buffers per device

Why:

- the service keeps a small bounded in-memory history useful for testing and future lightweight analysis

## Running Locally

### 1. Start the service

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Or package first:

```powershell
.\mvnw.cmd clean package
java -jar .\target\neurolive-realtime-service-0.0.1-SNAPSHOT.jar
```

### 2. Optional environment variables

```powershell
$env:DEVICE_TOKEN_ESP32_001="abc123"
$env:WS_ALLOWED_ORIGINS="*"
$env:HEARTBEAT_TIMEOUT_SECONDS="15"
```

### 3. Service URLs

- REST base: `http://localhost:8080`
- WebSocket endpoint: `ws://localhost:8080/ws/device`

## Railway Deployment

This repository is ready for Railway because:

- `server.port` is bound to `PORT`
- configuration is environment-variable friendly
- the project includes Maven Wrapper
- the Docker build enables the wrapper for Linux environments before packaging

### Suggested Railway environment variables

- `PORT=8080`
- `WS_ALLOWED_ORIGINS=*`
- `DEVICE_TOKEN_ESP32_001=abc123`
- `DEVICE_TOKEN_ESP32_002=def456`
- `HEARTBEAT_TIMEOUT_SECONDS=15`
- `HEARTBEAT_CHECK_INTERVAL_MILLIS=5000`
- `TELEMETRY_HISTORY_LIMIT=20`
- `BACKEND_BASE_URL=https://your-main-backend-url`

### Railway deploy options

Option 1:

- deploy directly from the repository using the included `Dockerfile`

Option 2:

- let Railway detect the Maven project automatically and run the wrapper build

## Manual Testing

See [websocket-test-examples.md](./websocket-test-examples.md) for:

- valid auth, telemetry, and heartbeat examples
- command dispatch and ack examples
- reconnection and re-authentication examples
- REST examples for commands and status
- expected behaviors for invalid cases

## Future Integration with the Main Backend

The repository already contains a placeholder integration contract:

- `BackendIntegrationService`
- `LoggingBackendIntegrationService`
- `BackendIntegrationObserver`

Today it only logs integration events.  
Later it can be replaced by a real HTTP client, message relay, or secure internal connector to:

- forward telemetry to the main backend
- notify disconnections
- propagate command acknowledgements
- report crisis or device-state events

## Current Limitations

- token validation is in-memory and simple by design
- telemetry is stored only in memory
- no persistent audit store is implemented here
- no advanced domain or clinical logic is included here
- no TLS termination is managed in-app; it should be handled by the deployment platform or gateway
- firmware-side fallback behavior must still be implemented on the ESP32 side

## Possible Future Improvements

- replace in-memory token validation with backend-driven validation
- persist telemetry and disconnect events when needed
- add more device actions beyond light control
- add metrics, tracing, and structured audit sinks
- support retry/outbox logic for backend forwarding
- add per-device rate limiting and stronger protocol validation

