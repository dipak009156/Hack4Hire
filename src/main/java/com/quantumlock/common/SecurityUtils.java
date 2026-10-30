package com.quantumlock.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Authentication> getAuthentication() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            return Optional.empty();
        }
        return Optional.of(context.getAuthentication());
    }

    public static Optional<String> getCurrentUsername() {
        return getAuthentication().map(Authentication::getName);
    }

    public static List<String> getCurrentRoles() {
        return getAuthentication()
                .map(Authentication::getAuthorities)
                .map(authorities -> authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    public static Optional<UUID> getCurrentTenantId() {
        return TenantContext.findTenantId();
    }

    public static UUID requireCurrentTenantId() {
        return TenantContext.requireTenantId();
    }
}
