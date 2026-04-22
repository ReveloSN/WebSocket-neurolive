package com.neurolive.realtime.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Centraliza la configuracion externa del servicio realtime.
@ConfigurationProperties(prefix = "realtime")
public class RealtimeProperties {

    private final WebSocket websocket = new WebSocket();
    private final Security security = new Security();
    private final Heartbeat heartbeat = new Heartbeat();
    private final Telemetry telemetry = new Telemetry();
    private final Integration integration = new Integration();

    // Expone la configuracion WebSocket.
    public WebSocket getWebsocket() {
        return websocket;
    }

    // Expone la configuracion de seguridad basica.
    public Security getSecurity() {
        return security;
    }

    // Expone la configuracion de heartbeat.
    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    // Expone la configuracion de telemetria.
    public Telemetry getTelemetry() {
        return telemetry;
    }

    // Expone la configuracion de integracion futura.
    public Integration getIntegration() {
        return integration;
    }

    // Agrupa las propiedades del endpoint WebSocket.
    public static class WebSocket {

        private String endpoint = "/ws/device";
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

        // Retorna la ruta del endpoint WebSocket.
        public String getEndpoint() {
            return endpoint;
        }

        // Define la ruta del endpoint WebSocket.
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        // Retorna los origenes permitidos.
        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        // Define los origenes permitidos.
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    // Agrupa la seguridad simple por token.
    public static class Security {

        private Map<String, String> deviceTokens = new HashMap<>();

        // Retorna el mapa de tokens por dispositivo.
        public Map<String, String> getDeviceTokens() {
            return deviceTokens;
        }

        // Define el mapa de tokens por dispositivo.
        public void setDeviceTokens(Map<String, String> deviceTokens) {
            this.deviceTokens = deviceTokens;
        }
    }

    // Agrupa la configuracion de monitoreo por heartbeat.
    public static class Heartbeat {

        private long timeoutSeconds = 15;
        private long checkIntervalMillis = 5_000;

        // Retorna el timeout de inactividad.
        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        // Define el timeout de inactividad.
        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        // Retorna el intervalo de revision.
        public long getCheckIntervalMillis() {
            return checkIntervalMillis;
        }

        // Define el intervalo de revision.
        public void setCheckIntervalMillis(long checkIntervalMillis) {
            this.checkIntervalMillis = checkIntervalMillis;
        }
    }

    // Agrupa la configuracion de almacenamiento de telemetria.
    public static class Telemetry {

        private int historyLimit = 20;

        // Retorna el limite del historial reciente.
        public int getHistoryLimit() {
            return historyLimit;
        }

        // Define el limite del historial reciente.
        public void setHistoryLimit(int historyLimit) {
            this.historyLimit = historyLimit;
        }
    }

    // Agrupa la configuracion para integracion futura.
    public static class Integration {

        private String baseUrl = "";
        private String internalToken = "";
        private long timeoutMs = 3_000;

        // Retorna la URL base del backend principal.
        public String getBaseUrl() {
            return baseUrl;
        }

        // Define la URL base del backend principal.
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        // Retorna el token interno para llamadas seguras.
        public String getInternalToken() {
            return internalToken;
        }

        // Define el token interno para llamadas seguras.
        public void setInternalToken(String internalToken) {
            this.internalToken = internalToken;
        }

        // Retorna el timeout de integracion HTTP.
        public long getTimeoutMs() {
            return timeoutMs;
        }

        // Define el timeout de integracion HTTP.
        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}

