package xpro.wang.kafkalab.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import xpro.wang.kafkalab.server.service.LabRealtimeWebSocketHandler;

/**
 * Registers WebSocket endpoints for realtime lab events.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final @NonNull LabRealtimeWebSocketHandler labRealtimeWebSocketHandler;

    public WebSocketConfig(@NonNull LabRealtimeWebSocketHandler labRealtimeWebSocketHandler) {
        this.labRealtimeWebSocketHandler = labRealtimeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(labRealtimeWebSocketHandler, "/ws/events")
                .setAllowedOriginPatterns("*");
    }
}
