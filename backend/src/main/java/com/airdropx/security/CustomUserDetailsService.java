package com.airdropx.security;

import com.airdropx.model.User;
import com.airdropx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * NOTE: unlike the typical Spring Security setup, "username" here is a stringified user ID
 * (the JWT subject claim), not an email. Login itself is handled directly in AuthService
 * (email + password lookup, no AuthenticationManager involved) since this API is fully
 * stateless — this service exists purely so JwtAuthFilter has a single, standard place
 * to re-hydrate the authenticated principal on every request.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public SecurityUser loadUserByUsername(String userId) {
        User user;
        try {
            user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new UsernameNotFoundException("No user with id " + userId));
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Malformed user id in token: " + userId);
        }
        return new SecurityUser(user);
    }
}
