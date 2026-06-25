package com.linkup.user;

import com.linkup.auth.AppUserPrincipal;
import com.linkup.user.dto.MeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * "Who am I" endpoint. Proves the full auth loop: a valid JWT → populated
 * SecurityContext → @AuthenticationPrincipal injects our AppUserPrincipal.
 *
 * The principal already carries the userId (from the token), so we fetch the
 * authoritative profile by id to return fresh display name / status.
 */
@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User no longer exists"));
        return ResponseEntity.ok(MeResponse.from(user));
    }
}
