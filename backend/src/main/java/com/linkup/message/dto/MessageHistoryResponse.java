package com.linkup.message.dto;

import java.util.List;

/**
 * A page of messages, always in ascending `seq` order (oldest → newest) for the client.
 * {@code hasMore} tells the client whether another page exists in the requested direction,
 * so it knows whether to keep offering "load earlier".
 */
public record MessageHistoryResponse(
        List<MessageResponse> messages,
        boolean hasMore
) {
}
