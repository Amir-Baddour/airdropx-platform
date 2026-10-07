package com.airdropx.security;

import com.airdropx.model.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());

        try {
            Claims claims = jwtService.parseAndValidate(token);
            String userId = claims.getSubject();

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                SecurityUser principal = userDetailsService.loadUserByUsername(userId);

                var authToken = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (JwtException | UsernameNotFoundException e) {
            // Invalid, expired, or refers to a deleted user: leave the context unauthenticated and let
            // SecurityConfig's authorization rules reject with 401/403 — this filter never throws directly,
            // so a bad token on a public endpoint (e.g. /api/v1/auth/login) doesn't break that request.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    // Bound to the User entity only for convenience in controllers that want the full entity, not just the principal.
    public static User currentUser() {
        var principal = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUser();
    }
}
