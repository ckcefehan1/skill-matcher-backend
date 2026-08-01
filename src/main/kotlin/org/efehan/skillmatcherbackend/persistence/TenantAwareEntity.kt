package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.TenantId

@MappedSuperclass
abstract class TenantAwareEntity(
    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    var companyId: String? = null,
) : AuditingBaseEntity()
