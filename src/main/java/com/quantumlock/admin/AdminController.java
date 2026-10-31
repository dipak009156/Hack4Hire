package com.quantumlock.admin;

import com.quantumlock.auth.AppUser;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.auth.AppUserRepository;
import com.quantumlock.auth.CreateUserRequest;
import com.quantumlock.auth.TenantRepository;
import com.quantumlock.auth.UpdateUserRequest;
import com.quantumlock.auth.UserResponse;
import com.quantumlock.document.RecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only operations.
 *
 * <ul>
 *   <li>{@code POST /api/v1/admin/reconcile}        — recover stale uploads (ADR-004)</li>
 *   <li>{@code GET  /api/v1/admin/users}             — list all users in caller's tenant</li>
 *   <li>{@code POST /api/v1/admin/users}             — create a new user in caller's tenant</li>
 *   <li>{@code PATCH /api/v1/admin/users/{id}}       — update role / status / password</li>
 *   <li>{@code DELETE /api/v1/admin/users/{id}}      — deactivate a user (soft-delete)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations — ADMIN role required")
public class AdminController {

    private final RecoveryService recoveryService;
    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    // ─── Reconcile ────────────────────────────────────────────────────────────

    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reconcile stale document uploads",
               description = "Marks PENDING/OBJECT_STORED uploads older than 5 min as FAILED or COMMITTED, and cleans up orphaned MinIO objects.")
    public ResponseEntity<Map<String, Object>> reconcile(
            @AuthenticationPrincipal AppUserDetails principal) {

        RecoveryService.ReconcileResult result = recoveryService.reconcile(
                principal.getUserId(), principal.getUsername());

        return ResponseEntity.ok(Map.of(
                "staleRecordsFound", result.staleRecordsFound(),
                "recovered", result.recovered(),
                "markedFailed", result.failed(),
                "objectsDeleted", result.objectsDeleted()
        ));
    }

    // ─── User Management ─────────────────────────────────────────────────────

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users in the admin's tenant")
    public ResponseEntity<List<UserResponse>> listUsers(
            @AuthenticationPrincipal AppUserDetails principal) {

        List<UserResponse> users = userRepository
                .findAllByTenantId(principal.getTenantId())
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user in the admin's tenant",
               description = "Tenant is derived from the caller's JWT — it cannot be overridden.")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {

        UUID tenantId = principal.getTenantId();

        // Prevent duplicate username within the same tenant
        if (userRepository.existsByTenantIdAndUsername(tenantId, request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username '" + request.username() + "' already exists in this tenant.");
        }

        // Default role is USER if none specified; validate allowed values
        List<String> roles = (request.roles() != null && !request.roles().isEmpty())
                ? request.roles().stream()
                        .filter(r -> List.of("ADMIN", "USER", "AUDITOR").contains(r))
                        .toList()
                : List.of("USER");

        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one valid role required (ADMIN, USER, AUDITOR).");
        }

        String trustLevel = (request.deviceTrustLevel() != null &&
                List.of("HIGH", "STANDARD").contains(request.deviceTrustLevel()))
                ? request.deviceTrustLevel()
                : "STANDARD";

        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .username(request.username().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(roles)
                .department(request.department())
                .deviceTrustLevel(trustLevel)
                .active(true)
                .build();

        AppUser saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PatchMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a user's roles, status, or password",
               description = "Admin cannot modify users from a different tenant.")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AppUserDetails principal) {

        AppUser user = userRepository.findByIdAndTenantId(id, principal.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found in your tenant."));

        // Prevent admin from deactivating themselves
        if (user.getId().equals(principal.getUserId()) &&
                request.active() != null && !request.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Admins cannot deactivate their own account.");
        }

        if (request.roles() != null && !request.roles().isEmpty()) {
            List<String> validated = request.roles().stream()
                    .filter(r -> List.of("ADMIN", "USER", "AUDITOR").contains(r))
                    .toList();
            if (!validated.isEmpty()) user.setRoles(validated);
        }
        if (request.department() != null) user.setDepartment(request.department());
        if (request.deviceTrustLevel() != null &&
                List.of("HIGH", "STANDARD").contains(request.deviceTrustLevel())) {
            user.setDeviceTrustLevel(request.deviceTrustLevel());
        }
        if (request.active() != null) user.setActive(request.active());
        if (request.newPassword() != null && request.newPassword().length() >= 8) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        return ResponseEntity.ok(toResponse(userRepository.save(user)));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate (soft-delete) a user",
               description = "Sets active=false. The user can no longer log in but their audit history is preserved.")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserDetails principal) {

        AppUser user = userRepository.findByIdAndTenantId(id, principal.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found in your tenant."));

        if (user.getId().equals(principal.getUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Admins cannot deactivate their own account.");
        }

        user.setActive(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserResponse toResponse(AppUser u) {
        return new UserResponse(
                u.getId(),
                u.getTenantId(),
                u.getUsername(),
                u.getRoles(),
                u.getDepartment(),
                u.getDeviceTrustLevel(),
                u.isActive(),
                u.getCreatedAt()
        );
    }
}
