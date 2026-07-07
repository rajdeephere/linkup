package com.linkup.push;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.push.dto.NotificationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The caller's notification history — the push-outbox entries addressed to them, newest first
 * (Day 11). Backs a client notification center and the {@code demo:push} assertion.
 *
 *   GET /v1/notifications
 */
@RestController
public class NotificationController {

    private final PushOutboxRepository outbox;

    public NotificationController(PushOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @GetMapping("/v1/notifications")
    public List<NotificationResponse> myNotifications(@AuthenticationPrincipal AppUserPrincipal principal) {
        return outbox.findByRecipientUserIdOrderByCreatedAtDesc(principal.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
