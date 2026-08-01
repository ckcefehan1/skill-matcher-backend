package org.efehan.skillmatcherbackend

import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SkillMatcherBackendApplication

fun main(args: Array<String>) {
    // Spring Data validates derived queries at bootstrap with no request in flight —
    // declared root on the startup thread; the JWT filter re-scopes every request.
    TenantContext.allowRoot()
    runApplication<SkillMatcherBackendApplication>(*args)
}
