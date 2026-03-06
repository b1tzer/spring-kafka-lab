package xpro.wang.kafkalab.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import xpro.wang.kafkalab.server.model.LabRealtimeEventType;

/** WebSocket handler for broadcasting realtime lab events to UI clients. */
@Component
public class LabRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LabRealtimeWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final LabActivityLogService labActivityLogService;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public LabRealtimeWebSocketHandler(ObjectMapper objectMapper, LabActivityLogService labActivityLogService) {
        this.objectMapper = objectMapper;
        this.labActivityLogService = labActivityLogService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        publishToSession(session, LabRealtimeEventType.ACTIVITY_LOG_SNAPSHOT.name(),
                Map.of("logs", labActivityLogService.recent(120)));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        sessions.remove(session);
    }

    public void publish(String type, Map<String, Object> data) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            String payload = objectMapper
                    .writeValueAsString(Map.of("type", type, "time", Instant.now().toString(), "data", data));

            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                session.sendMessage(new TextMessage(Objects.requireNonNull(payload)));
            }
        } catch (IOException ex) {
            log.warn("Failed to publish websocket event {}: {}", type, ex.getMessage());
        }
    }

    public void publish(LabRealtimeEventType eventType, Map<String, Object> data) {
        publish(eventType.name(), data);
    }

    private void publishToSession(WebSocketSession session, String type, Map<String, Object> data) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper
                    .writeValueAsString(Map.of("type", type, "time", Instant.now().toString(), "data", data));
            session.sendMessage(new TextMessage(Objects.requireNonNull(payload)));
        } catch (IOException ex) {
            log.warn("Failed to publish websocket event {} to session {}: {}", type, session.getId(), ex.getMessage());
        }
    }
}
