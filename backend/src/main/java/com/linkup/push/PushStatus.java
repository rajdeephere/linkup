package com.linkup.push;

/** Lifecycle of a push-outbox row. Stored as a string. */
public enum PushStatus {
    PENDING,   // enqueued, not yet handed to the sender
    SENT,      // the sender accepted it (FCM/APNs, or logged in dev)
    FAILED     // the sender rejected it after an attempt
}
