package com.linkup.common;

/** The request is semantically invalid (a cross-field rule failed) → mapped to 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
