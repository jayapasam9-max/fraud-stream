package com.fraudstream.processor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fraudstream.processor.model.FraudFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class FlagWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FlagWebSocketHandler.class);

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper mapper;

    public FlagWebSocketHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: {} (total: {})", session.getId(), sessions.size());
    }

    public void broadcast(FraudFlag flag) {
        if (sessions.isEmpty()) return;
        try {
            String json = mapper.writeValueAsString(flag);
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (Exception e) {
                    log.warn("Failed to send to session {}", session.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize FraudFlag for broadcast", e);
        }
    }
}
