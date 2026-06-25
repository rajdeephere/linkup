package com.linkup.common;

/** Thrown when registration is attempted with a username that already exists. */
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String username) {
        super("Username already taken: " + username);
    }
}
