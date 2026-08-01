package org.efehan.skillmatcherbackend.core.publicconfig

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.springframework.http.MediaType
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
// without produces, springdoc emits */* and the generated client types the response as Blob
@RequestMapping("/api/public/config", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Public config", description = "Unauthenticated runtime settings for the frontend.")
class PublicConfigController(
    private val standaloneProperties: StandaloneProperties,
) {
    @Operation(summary = "Read public runtime config")
    @GetMapping
    fun getPublicConfig() = PublicConfigResponse(registrationEnabled = !standaloneProperties.enabled)
}
