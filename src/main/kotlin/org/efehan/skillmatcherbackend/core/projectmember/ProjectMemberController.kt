package org.efehan.skillmatcherbackend.core.projectmember

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.core.application.ApplicationService
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@Tag(name = "Project Members", description = "Manage project team members")
class ProjectMemberController(
    private val service: ProjectMemberService,
    private val applicationService: ApplicationService,
) {
    @Operation(
        summary = "Add a member to a project",
        description = "Adds a user as an active member to a project. Only the project owner can add members.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Member added."),
            ApiResponse(
                responseCode = "400",
                description = "Validation error.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
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
                                {"errorCode": "PROJECT_ACCESS_DENIED",
                                "errorMessage": "Not allowed to modify this project."}
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project or user not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "User is already a member or project is full.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Duplicate",
                                value = """
                                {"errorCode": "PROJECT_MEMBER_DUPLICATE",
                                "errorMessage": "User is already a member of this project."}
                                """,
                            ),
                            ExampleObject(
                                name = "Full",
                                value = """
                                {"errorCode": "PROJECT_FULL",
                                "errorMessage": "Project has reached its maximum number of members."}
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
    fun addMember(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable projectId: String,
        @Valid @RequestBody request: AddProjectMemberRequest,
    ): ProjectMemberDto = applicationService.addMember(securityUser.user, projectId, request.userId).toDTO()

    @Operation(
        summary = "List active members of a project",
        description = "Returns all active members of a project. Any authenticated user can view this.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Members listed."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
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
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getMembers(
        @PathVariable projectId: String,
    ): List<ProjectMemberDto> = service.getMembers(projectId).map { it.toDTO() }

    @Operation(
        summary = "Remove a member from a project",
        description = "Sets a member's status to LEFT. Only the project owner can remove members.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Member removed.", content = [Content()]),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
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
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project, user, or member not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable projectId: String,
        @PathVariable userId: String,
    ) {
        service.removeMember(securityUser.user, projectId, userId)
    }

    @Operation(
        summary = "Leave a project",
        description = "Allows the authenticated user to leave a project they are a member of.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Left the project.", content = [Content()]),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project or membership not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveProject(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable projectId: String,
    ) {
        service.leaveProject(securityUser.user, projectId)
    }
}
