package com.linkup.user;

import com.linkup.common.ForbiddenException;
import com.linkup.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Device operations (Day 11): register the push token used to wake an offline device. */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public void registerPushToken(UUID userId, UUID deviceId, String pushToken) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found"));
        // A user may only register a token on their OWN device — the token comes from the session.
        if (!device.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your device");
        }
        device.setPushToken(pushToken);
        device.setLastSeenAt(Instant.now());
    }
}
