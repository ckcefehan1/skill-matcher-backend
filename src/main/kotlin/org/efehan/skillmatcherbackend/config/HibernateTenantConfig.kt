package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.core.tenant.TenantIdentifierResolver
import org.hibernate.cfg.AvailableSettings
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HibernateTenantConfig {
    @Bean
    fun tenantIdentifierResolverCustomizer(resolver: TenantIdentifierResolver) =
        HibernatePropertiesCustomizer { properties ->
            properties[AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = resolver
        }
}
