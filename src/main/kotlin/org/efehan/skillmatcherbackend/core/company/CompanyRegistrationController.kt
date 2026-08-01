package org.efehan.skillmatcherbackend.core.company

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.efehan.skillmatcherbackend.core.auth.AuthCookieService
import org.efehan.skillmatcherbackend.core.auth.AuthResponse
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/companies", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Company registration", description = "Public company self-registration (SaaS only).")
class CompanyRegistrationController(
    private val companyService: CompanyService,
    private val invitationService: InvitationService,
    private val authCookieService: AuthCookieService,
    private val standaloneProperties: StandaloneProperties,
) {
    @Operation(
        summary = "Register a company",
        description =
            "Creates a disabled company plus its first ADMIN and emails a 6-digit code. " +
                "The company is activated when the code flow completes. Always answered the same " +
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
    fun registerCompany(
        @Valid @RequestBody request: RegisterCompanyRequest,
    ) {
        requireSaas("/api/public/companies/register")
        companyService.provision(request.toProvision())
    }

    @Operation(
        summary = "Verify registration code",
        description =
            "Checks the 6-digit code without consuming it. Unknown email and wrong code " +
                "return the identical response, so the endpoint cannot enumerate registrations.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Check result."),
        ApiResponse(responseCode = "400", description = "Validation error."),
        ApiResponse(responseCode = "404", description = "Not available in standalone mode."),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded."),
    )
    @PostMapping("/verify")
    fun verifyCode(
        @Valid @RequestBody request: VerifyRegistrationCodeRequest,
    ): VerifyRegistrationCodeResponse {
        requireSaas("/api/public/companies/verify")
        return VerifyRegistrationCodeResponse(
            valid = invitationService.verifyRegistrationCode(request.email, request.code),
        )
    }

    @Operation(
        summary = "Complete registration",
        description =
            "Re-checks the code, sets password and profile, activates user and company. " +
                "Sets httpOnly access_token and refresh_token cookies.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Registration completed."),
        ApiResponse(responseCode = "400", description = "Invalid or expired code, or validation error."),
        ApiResponse(responseCode = "404", description = "Not available in standalone mode."),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded."),
    )
    @PostMapping("/complete")
    fun completeRegistration(
        @Valid @RequestBody request: CompleteRegistrationRequest,
        response: HttpServletResponse,
    ): AuthResponse {
        requireSaas("/api/public/companies/complete")
        val tokens =
            invitationService.completeRegistration(
                email = request.email,
                code = request.code,
                password = request.password,
                firstName = request.firstName,
                lastName = request.lastName,
            )
        authCookieService.addCookies(response, tokens.accessToken, tokens.refreshToken)
        return tokens.response
    }

    @Operation(
        summary = "Resend registration code",
        description =
            "Replaces the current code and restarts its validity. Always answered the " +
                "same way, whether the email is registered or not.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Request accepted."),
        ApiResponse(responseCode = "400", description = "Validation error."),
        ApiResponse(responseCode = "404", description = "Not available in standalone mode."),
        ApiResponse(responseCode = "429", description = "Rate limit exceeded."),
    )
    @PostMapping("/resend-code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resendCode(
        @Valid @RequestBody request: ResendRegistrationCodeRequest,
    ) {
        requireSaas("/api/public/companies/resend-code")
        invitationService.resendRegistrationCode(request.email)
    }

    // on-prem has exactly one company — self-registration is pointless and attack surface
    private fun requireSaas(path: String) {
        if (standaloneProperties.enabled) {
            throw EntryNotFoundException(
                resource = "Endpoint",
                field = "path",
                value = path,
                errorCode = GlobalErrorCode.NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )
        }
    }
}
