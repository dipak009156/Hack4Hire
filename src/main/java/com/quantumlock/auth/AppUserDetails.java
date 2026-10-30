package com.quantumlock.auth;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
public class AppUserDetails implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String username;
    private final String password;
    private final List<String> roles;
    private final String department;
    private final String deviceTrustLevel;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public AppUserDetails(AppUser user) {
        this.userId = user.getId();
        this.tenantId = user.getTenantId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.roles = user.getRoles() != null ? user.getRoles() : List.of();
        this.department = user.getDepartment();
        this.deviceTrustLevel = user.getDeviceTrustLevel();
        this.active = user.isActive();
        this.authorities = this.roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    public AppUserDetails(UUID userId, UUID tenantId, String username, List<String> roles,
                          String department, String deviceTrustLevel) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.password = "";
        this.roles = roles != null ? roles : List.of();
        this.department = department;
        this.deviceTrustLevel = deviceTrustLevel;
        this.active = true;
        this.authorities = this.roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
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
        return active;
    }
}
