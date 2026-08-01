package org.efehan.skillmatcherbackend.core.tenant

import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.stereotype.Component

/**
 * Null tenant = root context: Hibernate then skips the tenant restriction entirely.
 * That is deliberate for login/token flows, SUPERADMIN and background threads.
 */
@Component
class TenantIdentifierResolver : CurrentTenantIdentifierResolver<String> {
    override fun resolveCurrentTenantIdentifier(): String? = TenantContext.get()

    override fun validateExistingCurrentSessions(): Boolean = false
}
