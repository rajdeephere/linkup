package com.linkup.realtime;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.presence.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

/**
 * WebSocket session lifecycle → presence. On connect/disconnect we update the user's presence
 * in Redis (and fan out online/offline to their peers). Also logs the principal, which was the
 * Day-3 proof that the transport authenticates.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final PresenceService presence;

    public WebSocketEventListener(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = userId(accessor.getUser());
        String sessionId = accessor.getSessionId();
        log.info("WS CONNECTED   session={} user={}", sessionId, userId);
        if (userId != null) presence.onConnect(userId, sessionId);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = userId(accessor.getUser());
        String sessionId = accessor.getSessionId();
        log.info("WS DISCONNECTED session={} user={}", sessionId, userId);
        if (userId != null) presence.onDisconnect(userId, sessionId);
    }

    private UUID userId(Principal principal) {
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p.getId();
        }
        return null;
    }
}
