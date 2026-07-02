package com.linkup.conversation.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** Advance my read cursor to this seq (the highest message seq I've seen). */
public record ReadRequest(
        @PositiveOrZero long seq
) {
}
