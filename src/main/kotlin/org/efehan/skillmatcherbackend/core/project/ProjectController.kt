package org.efehan.skillmatcherbackend.core.project

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project management")
class ProjectController(
    private val service: ProjectService,
) {
    @Operation(
        summary = "Create a project",
        description = "Creates a new project. Only available for project managers.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Project created.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProjectDto::class),
                        examples = [
                            ExampleObject(
                                name = "Project created",
                                value = """
                                {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Skill Matcher",
                                    "description": "Internal tool to match employees to projects based on skills.",
                                    "status": "PLANNED",
                                    "startDate": "2026-03-01",
                                    "endDate": "2026-09-01",
                                    "maxMembers": 5,
                                    "ownerName": "Max Mustermann",
                                    "createdDate": "2026-02-10T12:00:00Z"
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
                                            "message": "must not be blank"
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
                description = "Not a project manager.",
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
    @PostMapping
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProject(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody request: CreateProjectRequest,
    ): ProjectDto =
        service
            .createProject(
                owner = securityUser.user,
                name = request.name,
                description = request.description,
                startDate = request.startDate,
                endDate = request.endDate,
                maxMembers = request.maxMembers,
            ).toDTO()

    @Operation(
        summary = "Get a project",
        description = "Returns a single project by ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Project retrieved successfully.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProjectDto::class),
                        examples = [
                            ExampleObject(
                                name = "Project found",
                                value = """
                                {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Skill Matcher",
                                    "description": "Internal tool to match employees to projects based on skills.",
                                    "status": "ACTIVE",
                                    "startDate": "2026-03-01",
                                    "endDate": "2026-09-01",
                                    "maxMembers": 5,
                                    "ownerName": "Max Mustermann",
                                    "createdDate": "2026-02-10T12:00:00Z"
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
                responseCode = "404",
                description = "Project not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not found",
                                value = """
                                {
                                    "errorCode": "PROJECT_NOT_FOUND",
                                    "errorMessage": "Project with id '550e8400-e29b-41d4-a716-446655440000' could not be found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun getProject(
        @PathVariable id: String,
    ): ProjectDto = service.getProject(id).toDTO()

    @Operation(
        summary = "Get all projects",
        description = "Returns all projects.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Projects retrieved successfully.",
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
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getAllProjects(
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<ProjectDto> = service.getAllProjects(pageable).map(ProjectModel::toDTO)

    @Operation(
        summary = "Update a project",
        description = "Updates an existing project. Only the owner can update.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Project updated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProjectDto::class),
                        examples = [
                            ExampleObject(
                                name = "Project updated",
                                value = """
                                {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Skill Matcher v2",
                                    "description": "Updated description for the project.",
                                    "status": "ACTIVE",
                                    "startDate": "2026-03-01",
                                    "endDate": "2026-12-01",
                                    "maxMembers": 8,
                                    "ownerName": "Max Mustermann",
                                    "createdDate": "2026-02-10T12:00:00Z"
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
                                            "message": "must not be blank"
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
                description = "Not the project owner.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "PROJECT_ACCESS_DENIED",
                                    "errorMessage": "Not allowed to modify Project."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not found",
                                value = """
                                {
                                    "errorCode": "PROJECT_NOT_FOUND",
                                    "errorMessage": "Project with id '550e8400-e29b-41d4-a716-446655440000' could not be found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    @ResponseStatus(HttpStatus.OK)
    fun updateProject(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateProjectRequest,
    ): ProjectDto =
        service
            .updateProject(
                user = securityUser.user,
                projectId = id,
                name = request.name,
                description = request.description,
                status = ProjectStatus.valueOf(request.status),
                startDate = request.startDate,
                endDate = request.endDate,
                maxMembers = request.maxMembers,
            ).toDTO()

    @Operation(
        summary = "Delete a project",
        description = "Deletes a project and its associated skills. Only the owner can delete.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Project deleted.",
                content = [Content()],
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
                description = "Not the project owner.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "PROJECT_ACCESS_DENIED",
                                    "errorMessage": "Not allowed to modify Project."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not found",
                                value = """
                                {
                                    "errorCode": "PROJECT_NOT_FOUND",
                                    "errorMessage": "Project with id '550e8400-e29b-41d4-a716-446655440000' could not be found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProject(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
    ) {
        service.deleteProject(securityUser.user, id)
    }
}
