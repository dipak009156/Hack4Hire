package com.quantumlock.auth;

import com.quantumlock.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Tenant-isolated authentication and user management APIs")
public class AuthController {

    private final AppUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TenantRepository tenantRepository;
    private final AppUserRepository userRepository;
    private final com.quantumlock.request.CredentialRequestService credentialRequestService;

    @GetMapping("/check-tenant")
    @Operation(summary = "Check if tenant name is available or duplicate", description = "Validates against registered tenants and pending requests.")
    public ResponseEntity<Map<String, Object>> checkTenantAvailability(
            @RequestParam(name = "name", required = false) String name) {
        return ResponseEntity.ok(credentialRequestService.checkTenantAvailability(name));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user within a tenant and issue JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AppUserDetails userDetails = userDetailsService.loadUserByUsernameAndTenant(
                request.username(),
                request.tenant()
        );

        if (!passwordEncoder.matches(request.password(), userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!userDetails.isEnabled()) {
            throw new BadCredentialsException("Account is inactive");
        }

        String token = jwtService.generateToken(
                userDetails,
                userDetails.getTenantId(),
                userDetails.getRoles(),
                userDetails.getDepartment(),
                userDetails.getDeviceTrustLevel()
        );

        LoginResponse response = new LoginResponse(
                token,
                "Bearer",
                jwtProperties.getExpirationMs() / 1000,
                userDetails.getTenantId().toString(),
                userDetails.getRoles(),
                userDetails.getUserId(),
                userDetails.getTenantId()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register-tenant")
    @Operation(summary = "Onboard a new tenant and create admin user")
    public ResponseEntity<Map<String, Object>> registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        Tenant tenant = tenantRepository.findByName(request.tenantName().trim())
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name(request.tenantName().trim())
                        .build()));

        AppUser admin = userRepository.findByUsernameAndTenantId(request.adminUsername().trim(), tenant.getId())
                .orElseGet(() -> userRepository.save(AppUser.builder()
                        .tenantId(tenant.getId())
                        .username(request.adminUsername().trim())
                        .passwordHash(passwordEncoder.encode(request.adminPassword()))
                        .roles(List.of("ADMIN"))
                        .department("security")
                        .deviceTrustLevel("HIGH")
                        .active(true)
                        .build()));

        AppUserDetails userDetails = new AppUserDetails(admin);
        String token = jwtService.generateToken(
                userDetails,
                tenant.getId(),
                admin.getRoles(),
                admin.getDepartment(),
                admin.getDeviceTrustLevel()
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("accessToken", token);
        resp.put("tokenType", "Bearer");
        resp.put("tenantId", tenant.getId());
        resp.put("tenantName", tenant.getName());
        resp.put("userId", admin.getId());
        resp.put("roles", admin.getRoles());

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/users")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new user within the authenticated tenant")
    public ResponseEntity<Map<String, Object>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {

        UUID tenantId = TenantContext.requireTenantId();

        AppUser user = userRepository.findByUsernameAndTenantId(request.username().trim(), tenantId)
                .orElseGet(() -> userRepository.save(AppUser.builder()
                        .tenantId(tenantId)
                        .username(request.username().trim())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .roles(request.roles())
                        .department("operations")
                        .deviceTrustLevel(request.clearanceLevel() != null && request.clearanceLevel() >= 2 ? "HIGH" : "STANDARD")
                        .active(true)
                        .build()));

        AppUserDetails userDetails = new AppUserDetails(user);
        String token = jwtService.generateToken(
                userDetails,
                tenantId,
                user.getRoles(),
                user.getDepartment(),
                user.getDeviceTrustLevel()
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("accessToken", token);
        resp.put("userId", user.getId());
        resp.put("username", user.getUsername());
        resp.put("roles", user.getRoles());
        resp.put("tenantId", tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
