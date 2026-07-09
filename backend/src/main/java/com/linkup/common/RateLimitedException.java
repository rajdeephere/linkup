package com.linkup.common;

/** The caller exceeded a rate limit → mapped to 429. */
public class RateLimitedException extends RuntimeException {
    public RateLimitedException(String message) {
        super(message);
    }
}
