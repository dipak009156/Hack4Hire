package com.quantumlock.auth;

import com.quantumlock.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public AppUserDetails loadUserByUsernameAndTenant(String username, String tenantIdentifier) {
        UUID tenantId = resolveTenantId(tenantIdentifier);
        AppUser user = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username + " in tenant " + tenantIdentifier));
        return new AppUserDetails(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new UsernameNotFoundException("Cannot load user without active TenantContext");
        }
        AppUser user = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username + " in tenant " + tenantId));
        return new AppUserDetails(user);
    }

    public UUID resolveTenantId(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            throw new IllegalArgumentException("Tenant identifier must not be null or blank");
        }
        try {
            UUID id = UUID.fromString(tenantIdentifier.trim());
            return tenantRepository.findById(id)
                    .map(Tenant::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantIdentifier));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Tenant not found")) {
                throw e;
            }
            return tenantRepository.findByName(tenantIdentifier.trim())
                    .map(Tenant::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found with name: " + tenantIdentifier));
        }
    }
}
