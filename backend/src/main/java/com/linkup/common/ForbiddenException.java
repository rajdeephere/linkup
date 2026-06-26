package com.linkup.common;

/** The caller is authenticated but not allowed to do this → mapped to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
