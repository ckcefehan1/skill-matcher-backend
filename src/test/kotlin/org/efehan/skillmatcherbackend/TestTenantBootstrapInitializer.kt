package org.efehan.skillmatcherbackend

import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/**
 * Spring Data validates derived queries while a test context boots — no request in
 * flight, no tenant. Registered via META-INF/spring.factories so every test context
 * load re-declares root on the loading thread (a TenantContext.clear() in one test
 * class must not break the next context's bootstrap).
 */
class TestTenantBootstrapInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TenantContext.allowRoot()
    }
}
