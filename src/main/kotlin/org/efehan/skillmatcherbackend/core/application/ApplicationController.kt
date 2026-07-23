package org.efehan.skillmatcherbackend.core.application

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Project Applications", description = "Apply to projects and manage applications")
class ApplicationController(
    private val service: ApplicationService,
) {
    @Operation(
        summary = "Apply to a project",
        description = "Creates a PENDING application for the authenticated user. Only EMPLOYER role can apply.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Application created.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ApplicationDto::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not EMPLOYER role.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Duplicate or already a member.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @PostMapping("/api/projects/{projectId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYER')")
    fun apply(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable projectId: String,
        @Valid @RequestBody request: CreateApplicationRequest,
    ): ApplicationDto =
        service
            .apply(
                user = securityUser.user,
                projectId = projectId,
                message = request.message,
            ).toDTO()

    @Operation(
        summary = "List applications for a project",
        description =
            "Returns all applications for a project, PENDING first by default (overridable via sort param). " +
                "Only the project owner (PROJECTMANAGER) can view.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Applications retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not the project owner.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Project not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping("/api/projects/{projectId}/applications")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    fun listForProject(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable projectId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<ApplicationDto> = service.listForProject(securityUser.user, projectId, pageable).map { it.toDTO() }

    @Operation(
        summary = "Accept an application",
        description = "Accepts a PENDING application, adds the user as a project member, and notifies the applicant.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Application accepted.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ApplicationDto::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not the project owner.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Application not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Already decided or project full.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @PostMapping("/api/applications/{id}/accept")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    fun accept(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
    ): ApplicationDto = service.accept(securityUser.user, id).toDTO()

    @Operation(
        summary = "Decline an application",
        description = "Declines a PENDING application and notifies the applicant.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Application declined.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ApplicationDto::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not the project owner.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Application not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Already decided.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @PostMapping("/api/applications/{id}/decline")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    fun decline(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
        @Valid @RequestBody(required = false) request: DecideApplicationRequest?,
    ): ApplicationDto =
        service
            .decline(
                pm = securityUser.user,
                applicationId = id,
                reason = request?.reason,
            ).toDTO()

    @Operation(
        summary = "Withdraw an application",
        description = "Withdraws the authenticated user's own PENDING application.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Application withdrawn.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ApplicationDto::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not the applicant.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Application not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Already decided.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @PostMapping("/api/applications/{id}/withdraw")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('EMPLOYER')")
    fun withdraw(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
    ): ApplicationDto = service.withdraw(securityUser.user, id).toDTO()

    @Operation(
        summary = "List my applications",
        description = "Returns all applications submitted by the authenticated user, newest first.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Applications retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping("/api/me/applications")
    @ResponseStatus(HttpStatus.OK)
    fun listForUser(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PageableDefault(size = 20, sort = ["appliedAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): Page<ApplicationDto> = service.listForUser(securityUser.user, pageable).map { it.toDTO() }
}
