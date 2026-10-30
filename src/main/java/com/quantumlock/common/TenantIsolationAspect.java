package com.quantumlock.common;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class TenantIsolationAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationAspect.class);

    @Pointcut("this(com.quantumlock.common.TenantAwareRepository)")
    public void tenantAwareRepositoryPointcut() {}

    @Before("tenantAwareRepositoryPointcut()")
    public void verifyTenantIsolation(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();

        // Check if TenantContext is present
        UUID currentTenant = TenantContext.getTenantId();

        // If TenantContext is present, verify no cross-tenant argument leak
        if (currentTenant != null) {
            for (Object arg : joinPoint.getArgs()) {
                if (arg instanceof UUID uuidArg) {
                    // If an argument is a tenantId parameter and does not match the authenticated context
                    String paramName = joinPoint.getSignature().toShortString();
                    if (paramName.toLowerCase().contains("tenant") && !uuidArg.equals(currentTenant)) {
                        log.error("Tenant isolation violation detected: Current tenant={}, attempted={}",
                                currentTenant, uuidArg);
                        throw new AccessDeniedException("Cross-tenant access violation");
                    }
                }
            }
        }
    }
}
