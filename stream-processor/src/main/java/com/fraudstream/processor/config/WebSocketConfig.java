package com.fraudstream.processor.config;

import com.fraudstream.processor.websocket.FlagWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final FlagWebSocketHandler flagWebSocketHandler;

    public WebSocketConfig(FlagWebSocketHandler flagWebSocketHandler) {
        this.flagWebSocketHandler = flagWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(flagWebSocketHandler, "/ws/flags")
                .setAllowedOriginPatterns("*");
    }
}
