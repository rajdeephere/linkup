package com.linkup.realtime;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.auth.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP session at CONNECT time.
 *
 * Why here (not the HTTP handshake): the WebSocket upgrade is a plain GET that browsers
 * can't attach custom auth headers to reliably, and the security model we want is "the
 * STOMP session has an identity." So we read the JWT from the CONNECT frame's
 * "Authorization" native header, verify it with the SAME JwtService the REST tier uses,
 * and bind the resulting principal to the session via accessor.setUser(...). From then on
 * every frame on that session carries that Principal — which is how /user/queue/** routing
 * and per-user authorization work downstream.
 *
 * A missing/invalid token throws, which makes Spring reject the CONNECT (no session).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Missing bearer token on STOMP CONNECT");
            }
            try {
                AppUserPrincipal principal = jwtService.parse(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                accessor.setUser(authentication); // binds identity to the WS session
            } catch (JwtException ex) {
                throw new MessageDeliveryException("Invalid token on STOMP CONNECT");
            }
        }
        return message;
    }
}
