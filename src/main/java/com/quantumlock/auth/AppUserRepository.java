package com.quantumlock.auth;

import com.quantumlock.common.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends TenantAwareRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameAndTenantId(String username, UUID tenantId);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);
}
