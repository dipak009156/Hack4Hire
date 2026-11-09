package com.quantumlock.auth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public static final UUID TENANT_ALPHA_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_BETA_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID TENANT_GAMMA_ID  = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Platform-level (not an isolated doc tenant). Owns the SUPER_ADMIN role. */
    public static final UUID PLATFORM_TENANT_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID SUPER_ADMIN_USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Override
    @Transactional
    public void run(String... args) {
        String hash = passwordEncoder.encode("password123");
        userRepository.findAll().forEach(u -> {
            u.setPasswordHash(hash);
            u.setActive(true);
            userRepository.save(u);
        });

        // Ensure platform tenant and superadmin exist
        Tenant platformTenant = tenantRepository.findByName("platform")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .id(PLATFORM_TENANT_ID)
                        .name("platform")
                        .build()));

        if (userRepository.findByUsernameAndTenantId("superadmin", platformTenant.getId()).isEmpty()) {
            userRepository.save(AppUser.builder()
                    .id(SUPER_ADMIN_USER_ID)
                    .tenantId(platformTenant.getId())
                    .username("superadmin")
                    .passwordHash(hash)
                    .roles(List.of("SUPER_ADMIN"))
                    .department("platform")
                    .deviceTrustLevel("HIGH")
                    .active(true)
                    .build());
            log.info("Initialized platform superadmin user.");
        }

        if (tenantRepository.count() > 1) {
            log.info("Database users updated with password123. Skipping initial tenant seeding.");
            return;
        }

        log.info("Initializing seed tenants and users...");

        seedTenantWithUsers(TENANT_ALPHA_ID, "tenant-alpha", "aaaa");
        seedTenantWithUsers(TENANT_BETA_ID, "tenant-beta", "bbbb");
        seedTenantWithUsers(TENANT_GAMMA_ID, "tenant-gamma", "cccc");

        log.info("Seed data initialization complete.");
    }

    private void seedTenantWithUsers(UUID tenantId, String tenantName, String hexMarker) {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .id(tenantId)
                .name(tenantName)
                .build());

        seedUser(UUID.fromString(tenantId.toString().substring(0, 9) + hexMarker + "-" + hexMarker + "-" + hexMarker + "-" + hexMarker.repeat(2) + "1"),
                tenant.getId(), "admin", List.of("ADMIN"), "security", "HIGH");

        seedUser(UUID.fromString(tenantId.toString().substring(0, 9) + hexMarker + "-" + hexMarker + "-" + hexMarker + "-" + hexMarker.repeat(2) + "2"),
                tenant.getId(), "user1", List.of("USER"), "engineering", "HIGH");

        seedUser(UUID.fromString(tenantId.toString().substring(0, 9) + hexMarker + "-" + hexMarker + "-" + hexMarker + "-" + hexMarker.repeat(2) + "3"),
                tenant.getId(), "user2", List.of("USER"), "finance", "STANDARD");

        seedUser(UUID.fromString(tenantId.toString().substring(0, 9) + hexMarker + "-" + hexMarker + "-" + hexMarker + "-" + hexMarker.repeat(2) + "4"),
                tenant.getId(), "auditor", List.of("AUDITOR"), "audit", "HIGH");
    }

    private void seedUser(UUID userId, UUID tenantId, String username, List<String> roles,
                          String department, String deviceTrustLevel) {
        userRepository.save(AppUser.builder()
                .id(userId)
                .tenantId(tenantId)
                .username(username)
                .passwordHash(passwordEncoder.encode("password123"))
                .roles(roles)
                .department(department)
                .deviceTrustLevel(deviceTrustLevel)
                .active(true)
                .build());
    }
}
