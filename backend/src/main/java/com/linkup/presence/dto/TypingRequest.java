package com.linkup.presence.dto;

/** Client → server typing signal. state = "start" | "stop". */
public record TypingRequest(String state) {
}
