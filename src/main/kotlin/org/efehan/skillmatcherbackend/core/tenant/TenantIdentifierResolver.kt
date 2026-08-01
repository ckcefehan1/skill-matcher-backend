package org.efehan.skillmatcherbackend.core.tenant

import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.stereotype.Component

/**
 * Hibernate 7 throws on a null tenant, so the resolver never returns one: the
 * ROOT sentinel marks the unfiltered root context (login, token flows, SUPERADMIN,
 * background threads). isRoot tells Hibernate to skip the tenant restriction for it.
 */
@Component
class TenantIdentifierResolver : CurrentTenantIdentifierResolver<String> {
    companion object {
        const val ROOT_TENANT = "__root__"
    }

    override fun resolveCurrentTenantIdentifier(): String = TenantContext.get() ?: ROOT_TENANT

    override fun isRoot(tenantId: String?): Boolean = tenantId == ROOT_TENANT

    override fun validateExistingCurrentSessions(): Boolean = false
}
