package com.linkup.auth;

import com.linkup.auth.dto.AuthResponse;
import com.linkup.auth.dto.LoginRequest;
import com.linkup.auth.dto.RegisterRequest;
import com.linkup.common.UsernameTakenException;
import com.linkup.user.Device;
import com.linkup.user.DeviceRepository;
import com.linkup.user.User;
import com.linkup.user.UserRepository;
import com.linkup.user.UserStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates registration and login, then mints a JWT.
 *
 * Why a service (not logic in the controller): the controller stays a thin HTTP
 * adapter (parse request → call service → map response), while the business rules
 * (uniqueness, hashing, token issuance) live here and are unit-testable without MVC.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       DeviceRepository deviceRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // Cheap pre-check for a friendly error; the DB unique constraint is the real
        // guarantee against the race where two registrations slip past this check.
        if (userRepository.existsByUsername(req.username())) {
            throw new UsernameTakenException(req.username());
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(req.username())
                .displayName(req.displayName())
                .passwordHash(passwordEncoder.encode(req.password())) // hash, never store plaintext
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        userRepository.save(user);

        Device device = newDevice(user, req.platform());
        deviceRepository.save(device);

        AppUserPrincipal principal =
                AppUserPrincipal.fromEntity(user.getId(), user.getUsername(), user.getPasswordHash());
        return issue(principal, user.getDisplayName(), device.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Delegates to Spring Security: this runs CustomUserDetailsService + BCrypt
        // verification and throws BadCredentialsException on a wrong username/password.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();

        User user = userRepository.findById(principal.getId()).orElseThrow();
        Device device = newDevice(user, req.platform());
        deviceRepository.save(device);

        return issue(principal, user.getDisplayName(), device.getId());
    }

    private Device newDevice(User user, com.linkup.user.Platform platform) {
        return Device.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(platform)
                .lastSeenAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private AuthResponse issue(AppUserPrincipal principal, String displayName, UUID deviceId) {
        String token = jwtService.generateToken(principal);
        return AuthResponse.bearer(
                token,
                jwtService.accessTokenTtlSeconds(),
                principal.getId(),
                principal.getUsername(),
                displayName,
                deviceId);
    }
}
