package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.hibernate.annotations.TenantId

/**
 * Seeded from [TenantContext] at construction rather than left to Hibernate: Hibernate only
 * fills `@TenantId` while flushing the insert and does not write the value back onto the
 * instance, so anything reading `companyId` before the flush (token minting, for one) would
 * see null. Root-context callers have no ambient tenant and assign it explicitly.
 */
@MappedSuperclass
abstract class TenantAwareEntity(
    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    var companyId: String? = TenantContext.get(),
) : AuditingBaseEntity()
