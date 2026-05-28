# Manual WebSocket Test Examples

## 1. Connect to the device channel

Example with `wscat`:

```bash
wscat -c ws://localhost:8080/ws/device
```

## 2. Authenticate the ESP32

For backend-integrated tests, use the registered device MAC as `deviceId` and configure
`DEVICE_TOKENS_JSON={"AA:BB:CC:DD:EE:29":"abc123"}`.

Send:

```json
{
  "type": "auth",
  "deviceId": "AA:BB:CC:DD:EE:29",
  "token": "abc123"
}
```

Expected response:

```json
{
  "type": "auth_ok",
  "deviceId": "AA:BB:CC:DD:EE:29",
  "timestamp": "2026-04-23T00:00:00+00:00"
}
```

## 3. Send telemetry

Valid ranges: `bpm` 25–250, `spo2` 50–100, `timestamp` > 0.

```json
{
  "type": "telemetry",
  "deviceId": "AA:BB:CC:DD:EE:29",
  "timestamp": 1710000000,
  "bpm": 92,
  "spo2": 98,
  "sensorConnected": true
}
```

After several samples, `GET /api/devices/AA:BB:CC:DD:EE:29/telemetry/latest` may include prediction fields:

```json
{
  "deviceId": "AA:BB:CC:DD:EE:29",
  "latestTelemetry": {
    "deviceId": "AA:BB:CC:DD:EE:29",
    "bpm": 92,
    "spo2": 98,
    "sensorConnected": true,
    "deviceTimestamp": 1710000000,
    "receivedAt": "2026-05-15T12:00:00+00:00",
    "predictionState": "STABLE",
    "predictionConfidence": 0.85,
    "predictionReasoning": "Trend within baseline"
  },
  "recentHistory": []
}
```

Prediction states: `STABLE`, `WARNING`, `PRE_CRISIS`, `INSUFFICIENT_DATA`.

## 4. Send heartbeat

```json
{
  "type": "heartbeat",
  "deviceId": "AA:BB:CC:DD:EE:29",
  "timestamp": 1710000001
}
```

## 5. Send ACK for a command

```json
{
  "type": "ack",
  "deviceId": "AA:BB:CC:DD:EE:29",
  "commandId": "cmd-001",
  "status": "applied"
}
```

## 6. Telemetry without auth

If telemetry is sent before `auth`, the gateway returns an error and closes the socket:

```json
{
  "type": "error",
  "code": "unauthenticated",
  "message": "Authenticate first"
}
```

## 7. Invalid JSON

If the payload is not valid JSON, the socket is closed with an `invalid_json` error.

## 8. Device mismatch

If the session is authenticated as `AA:BB:CC:DD:EE:29` and then sends a message with another `deviceId`,
the gateway closes the connection with `device_mismatch`.

## 9. Check connected devices

```bash
curl http://localhost:8080/api/devices/connected
```

## 10. Check device status

```bash
curl http://localhost:8080/api/devices/AA%3ABB%3ACC%3ADD%3AEE%3A29/status
```

## 11. Check latest telemetry

```bash
curl http://localhost:8080/api/devices/AA%3ABB%3ACC%3ADD%3AEE%3A29/telemetry/latest
```

## 12. Send a light command from REST

```bash
curl -X POST http://localhost:8080/api/devices/AA%3ABB%3ACC%3ADD%3AEE%3A29/commands/light \
  -H "Content-Type: application/json" \
  -d "{\"color\":\"#4A90E2\",\"intensity\":60,\"mode\":\"calm\"}"
```

Expected WebSocket message on the device side:

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

## 13. Read fallback config

```bash
curl http://localhost:8080/api/devices/fallback-config
```

Expected response:

```json
{
  "ledColor": "#4A90E2",
  "ledIntensity": 60,
  "ledMode": "calm",
  "heartbeatIntervalSeconds": 10,
  "description": "Calm mode - backend unavailable"
}
```

## 14. Health checks

```bash
curl http://localhost:8080/health
curl http://localhost:8080/actuator/health
```

## 15. Timeout scenario

1. Authenticate the device normally.
2. Stop sending both `telemetry` and `heartbeat`.
3. Wait longer than `HEARTBEAT_TIMEOUT_SECONDS`.
4. Expected result:
   - the socket closes
   - the device status eventually becomes `disconnected`
   - the backend receives `/internal/devices/{id}/disconnected` when integration is enabled

## 16. Reconnection scenario

1. Connect and authenticate as `AA:BB:CC:DD:EE:29`.
2. Close the socket or let it timeout.
3. Open a new WebSocket connection to `/ws/device`.
4. Send `auth` again with the same `deviceId` and token.
5. Expected result:
   - the new session becomes active
   - telemetry is accepted again only after the new `auth`
   - if an older session still existed, the newest one replaces it (close code `4000`, reason `session_replaced`)

## 17. WebSocket close codes (reference)

| Code | Meaning |
|------|---------|
| `4000` | Session replaced by a newer connection for the same device |
| `4001` | Heartbeat / activity timeout |
| `4003` | Invalid device credentials on `auth` |
| `4004` | Protocol violation (`unauthenticated`, `device_mismatch`, etc.) |
| `1003` | Invalid JSON or unsupported message type |
