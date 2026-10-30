package com.quantumlock.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {

    List<T> findAllByTenantId(UUID tenantId);

    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);

    boolean existsByIdAndTenantId(ID id, UUID tenantId);

    void deleteByIdAndTenantId(ID id, UUID tenantId);

    default List<T> findAllForCurrentTenant() {
        return findAllByTenantId(TenantContext.requireTenantId());
    }

    default Optional<T> findByIdForCurrentTenant(ID id) {
        return findByIdAndTenantId(id, TenantContext.requireTenantId());
    }

    default boolean existsByIdForCurrentTenant(ID id) {
        return existsByIdAndTenantId(id, TenantContext.requireTenantId());
    }

    default void deleteByIdForCurrentTenant(ID id) {
        deleteByIdAndTenantId(id, TenantContext.requireTenantId());
    }
}
