package com.linkup.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Logs WebSocket session lifecycle. For Day 3 this is how we *prove* the transport works:
 * two tabs connect → the server logs both authenticated principals (userIds).
 *
 * Later this becomes the hook for presence (mark user online/offline) and, in Phase 2, for
 * subscribing/unsubscribing the pod's Redis channels for the user's conversations.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        log.info("WS CONNECTED   session={} principal={}",
                accessor.getSessionId(), user != null ? user.getName() : "anonymous");
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        log.info("WS DISCONNECTED session={} principal={}",
                accessor.getSessionId(), user != null ? user.getName() : "anonymous");
    }
}
