package com.airdropx.security;

import com.airdropx.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adapts our User entity to what Spring Security's filter chain expects. The "ROLE_" prefix here is
 * required by Spring Security's hasRole(...) checks (SecurityConfig uses hasRole("PLATFORM_ADMIN"),
 * which internally looks for the authority "ROLE_PLATFORM_ADMIN" — the prefix is added here, once,
 * so the rest of the codebase never has to think about it).
 */
public class SecurityUser implements UserDetails {

    private final User user;

    public SecurityUser(User user) {
        this.user = user;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public UUID getCompanyId() {
        return user.getCompany() != null ? user.getCompany().getId() : null;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != com.airdropx.common.enums.CompanyStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == com.airdropx.common.enums.CompanyStatus.ACTIVE;
    }
}
