package com.linkup.user;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.user.dto.RegisterPushTokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Device endpoints (Day 11). A client registers its push token here after auth so the push
 * pipeline can wake it while offline.
 *
 *   PUT /v1/devices/{deviceId}/push-token   { pushToken }
 */
@RestController
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PutMapping("/v1/devices/{deviceId}/push-token")
    public ResponseEntity<Void> registerPushToken(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID deviceId,
            @Valid @RequestBody RegisterPushTokenRequest request) {
        deviceService.registerPushToken(principal.getId(), deviceId, request.pushToken());
        return ResponseEntity.noContent().build();
    }
}
