package com.quantumlock.auth;

import com.quantumlock.common.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        try {
            Claims claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            String tenantClaim = claims.get("tenant", String.class);
            UUID tenantId = UUID.fromString(tenantClaim);

            String uidClaim = claims.get("uid", String.class);
            UUID userId = uidClaim != null ? UUID.fromString(uidClaim) : null;

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            String department = claims.get("department", String.class);
            String deviceTrust = claims.get("device_trust", String.class);

            // Establish Tenant Context
            TenantContext.setTenantId(tenantId);

            List<SimpleGrantedAuthority> authorities = roles == null ? List.of() :
                    roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                            .collect(Collectors.toList());

            AppUserDetails principal = new AppUserDetails(
                    userId,
                    tenantId,
                    username,
                    roles,
                    department,
                    deviceTrust
            );

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(authToken);

            filterChain.doFilter(request, response);
        } finally {
            // Always clean up thread-local tenant context to prevent thread leakage
            TenantContext.clear();
        }
    }
}
