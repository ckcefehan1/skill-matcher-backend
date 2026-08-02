package org.efehan.skillmatcherbackend.core.role

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
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

/** Every company owns its own role catalog, so an admin only ever sees and edits their tenant's roles. */
@RestController
// without produces, springdoc emits */* and the generated client types every response as Blob
@RequestMapping("/api/admin/roles", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Roles", description = "Role catalog of the caller's company. ADMIN role required.")
class RoleController(
    private val roleService: RoleService,
) {
    @Operation(
        summary = "Create a role",
        description = "Creates a role for the caller's company. The name is stored uppercase.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Role created.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RoleDto::class),
                        examples = [
                            ExampleObject(
                                name = "Role created",
                                value = """
                                {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "AUDITOR",
                                    "description": "Reads audit logs",
                                    "builtIn": false
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation error.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Validation error",
                                value = """
                                {
                                    "errorCode": "VALIDATION_ERROR",
                                    "errorMessage": "Request validation failed.",
                                    "fieldErrors": [
                                        {
                                            "field": "name",
                                            "message": "name must start with a letter and contain only letters, digits and underscores"
                                        }
                                    ]
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not an admin.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "FORBIDDEN",
                                    "errorMessage": "Forbidden."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "The company already has a role with that name.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Duplicate name",
                                value = """
                                {
                                    "errorCode": "ROLE_ALREADY_EXISTS",
                                    "errorMessage": "Role already exists."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(
        @Valid @RequestBody request: CreateRoleRequest,
    ): RoleDto = roleService.create(request.name, request.description).toDTO()

    @Operation(
        summary = "List roles",
        description = "Lists the company's roles, built-in ones included.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Roles retrieved successfully.",
                content = [
                    Content(
                        mediaType = "application/json",
                        array = ArraySchema(schema = Schema(implementation = RoleDto::class)),
                        examples = [
                            ExampleObject(
                                name = "Role list",
                                value = """
                                [
                                    {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "name": "ADMIN",
                                        "description": "Company administrator",
                                        "builtIn": true
                                    },
                                    {
                                        "id": "550e8400-e29b-41d4-a716-446655440001",
                                        "name": "AUDITOR",
                                        "description": "Reads audit logs",
                                        "builtIn": false
                                    }
                                ]
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not an admin.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "FORBIDDEN",
                                    "errorMessage": "Forbidden."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun listRoles(): List<RoleDto> = roleService.listRoles().map { it.toDTO() }

    @Operation(
        summary = "Update a role description",
        description = "Updates the description. The name is immutable because it backs the granted authority.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Role updated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RoleDto::class),
                        examples = [
                            ExampleObject(
                                name = "Role updated",
                                value = """
                                {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "AUDITOR",
                                    "description": "Reads audit logs only",
                                    "builtIn": false
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation error.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Validation error",
                                value = """
                                {
                                    "errorCode": "VALIDATION_ERROR",
                                    "errorMessage": "Request validation failed.",
                                    "fieldErrors": [
                                        {
                                            "field": "description",
                                            "message": "description must not exceed 255 characters"
                                        }
                                    ]
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not an admin.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "FORBIDDEN",
                                    "errorMessage": "Forbidden."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Role not found in this company.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Role not found",
                                value = """
                                {
                                    "errorCode": "ROLE_NOT_FOUND",
                                    "errorMessage": "Role not found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
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
        value = [
            ApiResponse(responseCode = "204", description = "Role deleted."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not an admin.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "FORBIDDEN",
                                    "errorMessage": "Forbidden."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Role not found in this company.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Role not found",
                                value = """
                                {
                                    "errorCode": "ROLE_NOT_FOUND",
                                    "errorMessage": "Role not found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Role is built-in or still assigned to users.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Built-in role",
                                value = """
                                {
                                    "errorCode": "ROLE_IMMUTABLE",
                                    "errorMessage": "Built-in role 'ADMIN' cannot be deleted."
                                }
                                """,
                            ),
                            ExampleObject(
                                name = "Role in use",
                                value = """
                                {
                                    "errorCode": "ROLE_IN_USE",
                                    "errorMessage": "Role 'AUDITOR' is still assigned to users."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRole(
        @PathVariable roleId: String,
    ) {
        roleService.delete(roleId)
    }
}
