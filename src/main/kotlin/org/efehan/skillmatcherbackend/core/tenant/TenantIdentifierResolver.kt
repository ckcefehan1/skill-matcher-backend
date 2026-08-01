package org.efehan.skillmatcherbackend.core.tenant

import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.stereotype.Component

/**
 * Hibernate 7 throws on a null tenant, so the resolver never returns one: the
 * ROOT sentinel marks the explicitly declared root context (login, token flows,
 * SUPERADMIN). isRoot tells Hibernate to skip the tenant restriction for it.
 * An empty TenantContext without explicit root marking is a bug and throws.
 */
@Component
class TenantIdentifierResolver : CurrentTenantIdentifierResolver<String> {
    companion object {
        const val ROOT_TENANT = "__root__"
    }

    override fun resolveCurrentTenantIdentifier(): String =
        TenantContext.get()
            ?: if (TenantContext.isRootExplicit()) {
                ROOT_TENANT
            } else {
                throw IllegalStateException(
                    "No tenant in TenantContext — set one or declare the path root via TenantContext.runAsRoot {}",
                )
            }

    override fun isRoot(tenantId: String?): Boolean = tenantId == ROOT_TENANT

    override fun validateExistingCurrentSessions(): Boolean = false
}
