# WebSocket Test Examples

## WebSocket Endpoint

- Local: `ws://localhost:8080/ws/device`
- Railway: `wss://<your-railway-domain>/ws/device`

## Suggested Tools

- Postman WebSocket client
- `wscat`

Install `wscat` if needed:

```bash
npm install -g wscat
```

Connect locally:

```bash
wscat -c ws://localhost:8080/ws/device
```

## 1. Authenticate

Send:

```json
{
  "type": "auth",
  "deviceId": "ESP32_001",
  "token": "abc123"
}
```

Expected response:

```json
{
  "type": "auth_result",
  "deviceId": "ESP32_001",
  "authenticated": true,
  "message": "Authentication successful"
}
```

## 2. Send telemetry

Send:

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

Expected behavior:

- message is accepted
- latest telemetry is stored
- device activity is updated

## 3. Send heartbeat

Send:

```json
{
  "type": "heartbeat",
  "deviceId": "ESP32_001",
  "timestamp": 1710000001
}
```

Expected behavior:

- heartbeat is accepted
- last activity is refreshed
- timeout counter is effectively reset

## 4. Receive a light command

REST request:

```bash
curl -X POST http://localhost:8080/api/devices/ESP32_001/commands/light \
  -H "Content-Type: application/json" \
  -d "{\"color\":\"#4A90E2\",\"intensity\":60,\"mode\":\"calm\"}"
```

Expected WebSocket message on the device connection:

```json
{
  "type": "command",
  "commandId": "generated-uuid",
  "action": "set_light",
  "payload": {
    "color": "#4A90E2",
    "intensity": 60,
    "mode": "calm"
  }
}
```

## 5. Send ack for a command

Send:

```json
{
  "type": "ack",
  "deviceId": "ESP32_001",
  "commandId": "generated-uuid",
  "status": "applied"
}
```

Expected behavior:

- ack is accepted
- internal event is published
- mock backend integration logs the acknowledgement

## REST Checks

### Connected devices

```bash
curl http://localhost:8080/api/devices/connected
```

### Device status

```bash
curl http://localhost:8080/api/devices/ESP32_001/status
```

### Latest telemetry

```bash
curl http://localhost:8080/api/devices/ESP32_001/telemetry/latest
```

## Negative Tests

### Invalid JSON

Send:

```json
{ "type":
```

Expected behavior:

- service sends an `error` message
- connection is closed

### Telemetry before auth

Send telemetry without sending `auth` first.

Expected behavior:

- service sends an `error` message
- connection is closed

### Device mismatch

Authenticate as `ESP32_001` and then send:

```json
{
  "type": "heartbeat",
  "deviceId": "ESP32_999",
  "timestamp": 1710000002
}
```

Expected behavior:

- service sends an `error` message
- connection is closed because the message device does not match the authenticated session

### Heartbeat timeout

Authenticate and stop sending telemetry/heartbeat for more than the configured timeout.

Expected behavior:

- the service marks the device as disconnected
- the WebSocket session is closed
- a disconnect event is logged

