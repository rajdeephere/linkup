package com.linkup.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PushOutboxRepository extends JpaRepository<PushOutbox, UUID> {

    /** Idempotency guard — has this exact (message, device) push already been enqueued? */
    boolean existsByMessageIdAndDeviceId(UUID messageId, UUID deviceId);

    /** A user's notification history, newest first (backs GET /v1/notifications). */
    List<PushOutbox> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);
}
