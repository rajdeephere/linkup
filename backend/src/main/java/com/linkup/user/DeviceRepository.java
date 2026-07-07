package com.linkup.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByUserId(UUID userId);

    /** Devices of a user that can receive a push (have a registered token) — the push targets. */
    List<Device> findByUserIdAndPushTokenIsNotNull(UUID userId);
}
