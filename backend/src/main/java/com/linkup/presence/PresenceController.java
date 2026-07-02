package com.linkup.presence;

import com.linkup.presence.dto.PresenceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** On-demand presence lookup (the real-time push is via /user/queue/presence). */
@RestController
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/v1/users/{id}/presence")
    public PresenceResponse presence(@PathVariable UUID id) {
        return presenceService.presenceOf(id);
    }
}
