package com.quantumlock.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Tenant-isolated authentication APIs")
public class AuthController {

    private final AppUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

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
                userDetails.getRoles()
        );

        return ResponseEntity.ok(response);
    }
}
