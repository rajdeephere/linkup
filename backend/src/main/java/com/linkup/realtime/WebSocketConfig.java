package com.linkup.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration.
 *
 * Destinations:
 *  - "/app/**"   → messages bound for @MessageMapping handlers (client → server SENDs)  [Day 4]
 *  - "/user/**"  → per-user queues; Spring resolves /user/queue/x to THIS user's session
 *  - "/topic", "/queue" → the in-memory simple broker (fine for one node; becomes a Redis/
 *                          external relay when we scale to many WS pods in Phase 2)
 *
 * The client connects to ws://host/ws (native WebSocket — no SockJS, so the only client dep
 * is @stomp/stompjs). The JWT auth happens on the CONNECT frame via the interceptor below.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:4200"); // Angular dev origin
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue"); // single-node in-memory broker (Phase 0)
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Every inbound frame passes through here; the interceptor authenticates CONNECT.
        registration.interceptors(authInterceptor);
    }
}
