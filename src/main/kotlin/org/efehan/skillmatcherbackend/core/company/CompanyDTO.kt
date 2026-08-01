package org.efehan.skillmatcherbackend.core.company

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
    val isEnabled: Boolean,
    val createdDate: Instant?,
)

data class CreateCompanyRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val street: String,
    @field:NotBlank val zip: String,
    @field:NotBlank val city: String,
    @field:NotBlank @field:Size(min = 2, max = 2) val country: String,
    val industry: String? = null,
    val companySize: CompanySize? = null,
    val website: String? = null,
    @field:NotBlank @field:Email val adminEmail: String,
)

data class UpdateCompanyStatusRequest(
    val enabled: Boolean,
)
