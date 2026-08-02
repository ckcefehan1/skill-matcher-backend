package org.efehan.skillmatcherbackend.core.role

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Roles are global, not tenant-owned — a company admin editing them would reach into every other tenant. */
@RestController
// without produces, springdoc emits */* and the generated client types every response as Blob
@RequestMapping("/api/superadmin/roles", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Roles", description = "Global role catalog. SUPERADMIN role required.")
class RoleController(
    private val roleService: RoleService,
) {
    @Operation(summary = "Create a role", description = "Creates a global role. The name is stored uppercase.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Role created."),
        ApiResponse(responseCode = "400", description = "Validation error."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
        ApiResponse(responseCode = "409", description = "Role name already exists."),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(
        @Valid @RequestBody request: CreateRoleRequest,
    ): RoleDto = roleService.create(request.name, request.description).toDTO()

    @Operation(summary = "List roles", description = "Lists all global roles, built-in ones included.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Roles retrieved."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
    )
    @GetMapping
    fun listRoles(): List<RoleDto> = roleService.listRoles().map { it.toDTO() }

    @Operation(
        summary = "Update a role description",
        description = "Updates the description. The name is immutable because it backs the granted authority.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Role updated."),
        ApiResponse(responseCode = "400", description = "Validation error."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
        ApiResponse(responseCode = "404", description = "Role not found."),
    )
    @PatchMapping("/{roleId}")
    fun updateRole(
        @PathVariable roleId: String,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): RoleDto = roleService.updateDescription(roleId, request.description).toDTO()

    @Operation(
        summary = "Delete a role",
        description = "Deletes a custom role. Built-in roles and roles still assigned to users are rejected.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Role deleted."),
        ApiResponse(responseCode = "403", description = "SUPERADMIN role required."),
        ApiResponse(responseCode = "404", description = "Role not found."),
        ApiResponse(responseCode = "409", description = "Role is built-in or still assigned to users."),
    )
    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRole(
        @PathVariable roleId: String,
    ) {
        roleService.delete(roleId)
    }
}
