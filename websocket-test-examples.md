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

## 1. Valid auth

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

Notes:

- this is the required first step for every new WebSocket session
- if the device reconnects later, it must authenticate again

## 2. Valid telemetry

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
- `timestamp` must be present and greater than zero
- `bpm` must be within the accepted range
- `spo2` must be within the accepted range

## 3. Valid heartbeat

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

## 4. Command dispatch

REST request:

```bash
curl -X POST http://localhost:8080/api/devices/ESP32_001/commands/light \
  -H "Content-Type: application/json" \
  -d "{\"color\":\"#4A90E2\",\"intensity\":60,\"mode\":\"calm\"}"
```

Expected REST response:

```json
{
  "deviceId": "ESP32_001",
  "commandId": "generated-uuid",
  "action": "set_light",
  "status": "sent",
  "sentAt": "2026-04-22T00:00:00Z"
}
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

Notes:

- `color` must use the format `#RRGGBB`
- `mode` must be one of `calm`, `steady`, or `pulse`

## 5. Ack

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

## 6. Telemetry without auth

Send a valid telemetry message before sending `auth`.

Example:

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

- service sends an `error` message
- connection is closed

## 7. Mismatched deviceId

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

## 8. Timeout

Authenticate and stop sending telemetry or heartbeat for more than the configured timeout.

Expected behavior:

- the service marks the device as disconnected
- the WebSocket session is closed
- a disconnect event is logged

## 9. Reconnection

1. Connect and authenticate normally.
2. Let the session close because of timeout, or close the client manually.
3. Open a new WebSocket connection to `ws://localhost:8080/ws/device`.
4. Send the `auth` message again.
5. Resume heartbeat and telemetry traffic.

Expected behavior:

- the new socket is treated as a new session
- previous authentication state is not reused automatically
- telemetry and heartbeat are accepted only after the new `auth` message
- if the same `deviceId` reconnects, the newest authenticated session becomes the active one

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

Authenticate and stop sending telemetry or heartbeat for more than the configured timeout.

Expected behavior:

- the service marks the device as disconnected
- the WebSocket session is closed
- a disconnect event is logged

