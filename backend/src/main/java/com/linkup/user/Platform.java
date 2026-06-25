package com.linkup.user;

/**
 * Device platform. Multi-device is first-class in LinkUp: message fan-out targets
 * DEVICES, not users, so every connection is tied to one Device row.
 */
public enum Platform {
    WEB,
    IOS,
    ANDROID
}
