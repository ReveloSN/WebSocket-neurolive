package com.neurolive.realtime.config;

import com.neurolive.realtime.websocket.IoTWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// Registra el endpoint WebSocket raw para los dispositivos.
@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final IoTWebSocketHandler ioTWebSocketHandler;
    private final RealtimeProperties realtimeProperties;

    // Recibe las dependencias de configuración WebSocket.
    public RealtimeWebSocketConfig(IoTWebSocketHandler ioTWebSocketHandler,
                                   RealtimeProperties realtimeProperties) {
        this.ioTWebSocketHandler = ioTWebSocketHandler;
        this.realtimeProperties = realtimeProperties;
    }

    // Registra el handler raw en la ruta configurada.
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ioTWebSocketHandler, realtimeProperties.getWebsocket().getEndpoint())
                .setAllowedOrigins(realtimeProperties.getWebsocket().getAllowedOrigins().toArray(String[]::new));
    }
}

