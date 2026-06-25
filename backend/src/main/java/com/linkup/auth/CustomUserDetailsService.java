package com.linkup.auth;

import com.linkup.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a user by username for the LOGIN path. Spring's DaoAuthenticationProvider
 * calls this, then compares the submitted password against getPassword() using
 * the configured PasswordEncoder.
 *
 * This is only used at login — per-request authentication is rebuilt from the JWT
 * claims (see JwtAuthenticationFilter), so we are NOT hitting the DB on every call.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(u -> AppUserPrincipal.fromEntity(u.getId(), u.getUsername(), u.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + username));
    }
}
