package org.efehan.skillmatcherbackend.core.company

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/companies")
@Tag(name = "Company registration", description = "Public company self-registration (SaaS only).")
class CompanyRegistrationController(
    private val companyService: CompanyService,
    private val standaloneProperties: StandaloneProperties,
) {
    @Operation(
        summary = "Register a company",
        description =
            "Creates a disabled company plus its first ADMIN and sends an invitation email. " +
                "The company is activated when the invite is accepted. Always answered the same " +
                "way, whether the email or the company name is already taken or not.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Registration accepted."),
        ApiResponse(responseCode = "400", description = "Validation error."),
        ApiResponse(responseCode = "404", description = "Not available in standalone mode."),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded."),
    )
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun register(
        @Valid @RequestBody request: RegisterCompanyRequest,
    ) {
        // on-prem has exactly one company — self-registration is pointless and attack surface
        if (standaloneProperties.enabled) {
            throw EntryNotFoundException(
                resource = "Endpoint",
                field = "path",
                value = "/api/public/companies/register",
                errorCode = GlobalErrorCode.NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )
        }
        companyService.provision(request.toProvision())
    }
}
