package com.quantumlock.common;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
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
        UUID currentTenant = TenantContext.getTenantId();
        if (currentTenant == null) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        if (joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            String[] paramNames = methodSignature.getParameterNames();
            if (paramNames != null && paramNames.length == args.length) {
                boolean verifiedAny = false;
                for (int i = 0; i < paramNames.length; i++) {
                    String pName = paramNames[i].toLowerCase();
                    if (pName.contains("tenant") && args[i] instanceof UUID uuidArg) {
                        verifiedAny = true;
                        if (!uuidArg.equals(currentTenant)) {
                            log.error("Tenant isolation violation detected: Current tenant={}, attempted={}",
                                    currentTenant, uuidArg);
                            throw new AccessDeniedException("Cross-tenant access violation");
                        }
                    }
                }
                if (verifiedAny) {
                    return;
                }
            }
        }

        // Method-name convention fallback if parameter names are omitted by proxy
        String methodName = joinPoint.getSignature().getName();
        if ("findAllByTenantId".equals(methodName) || "findActiveByTenantId".equals(methodName)) {
            checkTenantArg(args, 0, currentTenant);
        } else if ("findByIdAndTenantId".equals(methodName) 
                || "existsByIdAndTenantId".equals(methodName) 
                || "deleteByIdAndTenantId".equals(methodName)
                || "findByDocumentIdAndTenantIdAndVersion".equals(methodName)
                || "findAllByDocumentIdAndTenantIdOrderByVersionDesc".equals(methodName)
                || "findTopByDocumentIdAndTenantIdOrderByVersionDesc".equals(methodName)) {
            checkTenantArg(args, 1, currentTenant);
        }
    }

    private void checkTenantArg(Object[] args, int index, UUID currentTenant) {
        if (args != null && index < args.length && args[index] instanceof UUID uuidArg) {
            if (!uuidArg.equals(currentTenant)) {
                log.error("Tenant isolation violation detected: Current tenant={}, attempted={}",
                        currentTenant, uuidArg);
                throw new AccessDeniedException("Cross-tenant access violation");
            }
        }
    }
}
