package org.efehan.skillmatcherbackend.core.company

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.efehan.skillmatcherbackend.persistence.CompanySize
import java.time.Instant

data class CompanyResponse(
    val id: String,
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val industry: String?,
    val companySize: String?,
    val website: String?,
    // not isEnabled: springdoc would name the schema property `enabled` while Jackson
    // still writes `isEnabled`, and the generated client reads the field that never arrives
    val enabled: Boolean,
    val createdDate: Instant?,
)

/** Superadmin endpoint. Deliberately separate from [RegisterCompanyRequest]: a field
 *  added here later must never become publicly settable by accident. */
data class CreateCompanyRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val street: String,
    @field:NotBlank val zip: String,
    @field:NotBlank val city: String,
    @field:NotBlank @field:Pattern(regexp = "[A-Z]{2}") val country: String,
    val industry: String? = null,
    val companySize: CompanySize? = null,
    val website: String? = null,
    @field:NotBlank @field:Email val adminEmail: String,
) {
    fun toProvision() =
        ProvisionCompanyCommand(
            name = name,
            street = street,
            zip = zip,
            city = city,
            country = country,
            industry = industry,
            companySize = companySize,
            website = website,
            adminEmail = adminEmail,
            selfRegistered = false,
        )
}

/** Public self-registration (SaaS only). */
data class RegisterCompanyRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val street: String,
    @field:NotBlank val zip: String,
    @field:NotBlank val city: String,
    @field:NotBlank @field:Pattern(regexp = "[A-Z]{2}") val country: String,
    val industry: String? = null,
    val companySize: CompanySize? = null,
    val website: String? = null,
    @field:NotBlank @field:Email val adminEmail: String,
) {
    fun toProvision() =
        ProvisionCompanyCommand(
            name = name,
            street = street,
            zip = zip,
            city = city,
            country = country,
            industry = industry,
            companySize = companySize,
            website = website,
            adminEmail = adminEmail,
            selfRegistered = true,
        )
}

/** Internal company-provisioning input, built from the two endpoint DTOs. */
data class ProvisionCompanyCommand(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val industry: String?,
    val companySize: CompanySize?,
    val website: String?,
    val adminEmail: String,
    val selfRegistered: Boolean,
)

data class UpdateCompanyStatusRequest(
    val enabled: Boolean,
)

/** Step 1 of the code flow: checked when the 6th digit lands. Never 401/403 —
 *  the SPA treats those as expired session / CSRF and would log the user out. */
data class VerifyRegistrationCodeRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Pattern(regexp = "\\d{6}") val code: String,
)

data class VerifyRegistrationCodeResponse(
    val valid: Boolean,
)

/** Step 2: re-checks the code (that check IS the authentication), sets password
 *  and profile, activates user and company. */
data class CompleteRegistrationRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Pattern(regexp = "\\d{6}") val code: String,
    @field:NotBlank val password: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
)

/** Always 202 — the answer must not reveal whether the email is registered. */
data class ResendRegistrationCodeRequest(
    @field:NotBlank @field:Email val email: String,
)
