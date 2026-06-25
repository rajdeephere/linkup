package com.linkup.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal that flows through Spring Security and lands in
 * @AuthenticationPrincipal on controllers.
 *
 * Crucially it carries the userId (UUID), not just the username — every downstream
 * feature (conversations, messages, receipts) keys off userId, so we want it on the
 * principal directly and never have to re-look-it-up from the username.
 *
 * Two construction paths:
 *  - fromEntity(...): used at LOGIN, includes the password hash so Spring can verify it.
 *  - fromClaims(...): used per-request from a verified JWT, password is null (not needed).
 */
public class AppUserPrincipal implements UserDetails {

    private static final List<GrantedAuthority> DEFAULT_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final UUID id;
    private final String username;
    private final String passwordHash; // nullable (only present on the login path)

    private AppUserPrincipal(UUID id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal fromEntity(UUID id, String username, String passwordHash) {
        return new AppUserPrincipal(id, username, passwordHash);
    }

    public static AppUserPrincipal fromClaims(UUID id, String username) {
        return new AppUserPrincipal(id, username, null);
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return DEFAULT_AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
