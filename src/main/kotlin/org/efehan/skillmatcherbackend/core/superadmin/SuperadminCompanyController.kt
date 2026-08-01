package org.efehan.skillmatcherbackend.core.superadmin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.core.company.CompanyResponse
import org.efehan.skillmatcherbackend.core.company.CompanyService
import org.efehan.skillmatcherbackend.core.company.CreateCompanyRequest
import org.efehan.skillmatcherbackend.core.company.UpdateCompanyStatusRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
// without produces, springdoc emits */* and the generated client types every response as Blob
@RequestMapping("/api/superadmin/companies", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Superadmin", description = "Platform-level company management. SUPERADMIN role required.")
class SuperadminCompanyController(
    private val companyService: CompanyService,
) {
    @Operation(
        summary = "Create a company",
        description = "Creates an enabled company and invites its first ADMIN user.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Company created."),
        ApiResponse(responseCode = "400", description = "Validation error."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
        ApiResponse(responseCode = "409", description = "Company name or admin email already exists."),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCompany(
        @Valid @RequestBody request: CreateCompanyRequest,
    ): CompanyResponse = companyService.provision(request.toProvision())!!.toDTO()

    @Operation(summary = "List companies", description = "Lists all companies across all tenants.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Companies retrieved."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
    )
    @GetMapping
    fun listCompanies(): List<CompanyResponse> = companyService.list().map { it.toDTO() }

    @Operation(summary = "Enable or disable a company", description = "Disabling locks the whole tenant out (403 on requests).")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Status updated."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
        ApiResponse(responseCode = "404", description = "Company not found."),
    )
    @PatchMapping("/{companyId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateCompanyStatus(
        @PathVariable companyId: String,
        @Valid @RequestBody request: UpdateCompanyStatusRequest,
    ) {
        companyService.setEnabled(companyId, request.enabled)
    }
}
