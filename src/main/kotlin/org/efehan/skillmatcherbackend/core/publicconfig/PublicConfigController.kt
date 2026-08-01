package org.efehan.skillmatcherbackend.core.publicconfig

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PublicConfigResponse(
    val registrationEnabled: Boolean,
)

/**
 * Runtime settings the login screen needs before anyone is authenticated. SaaS and
 * on-prem ship the same image, so the mode cannot be baked into the frontend bundle.
 */
@RestController
@RequestMapping("/api/public/config")
@Tag(name = "Public config", description = "Unauthenticated runtime settings for the frontend.")
class PublicConfigController(
    private val standaloneProperties: StandaloneProperties,
) {
    @Operation(summary = "Read public runtime config")
    @GetMapping
    fun get() = PublicConfigResponse(registrationEnabled = !standaloneProperties.enabled)
}
