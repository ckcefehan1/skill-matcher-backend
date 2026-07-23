package org.efehan.skillmatcherbackend.core.matching

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/matching")
@Tag(name = "Matching", description = "Skill-based matching between users and projects")
class MatchingController(
    private val matchingService: MatchingService,
) {
    @Operation(
        summary = "Find matching candidates for a project",
        description = "Returns a ranked list of users that the projects required skills.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Matching candidates found.",
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
    @GetMapping("/projects/{projectId}/candidates")
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    @ResponseStatus(HttpStatus.OK)
    fun findCandidates(
        @PathVariable
        projectId: String,
        @RequestParam(defaultValue = "0.0")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        minScore: Double,
        @RequestParam(defaultValue = "20")
        @Min(1)
        limit: Int,
        @RequestParam(defaultValue = "ALL")
        tier: MatchTier,
    ): List<UserMatchDto> = matchingService.findCandidatesForProject(projectId, minScore, limit, tier)

    @Operation(
        summary = "Find matching projects for me",
        description = "Returns a ranked list of projects that match the users skills.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Matching projects found.",
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
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/me/projects")
    @ResponseStatus(HttpStatus.OK)
    fun findProjectsForMe(
        @AuthenticationPrincipal
        securityUser: SecurityUser,
        @RequestParam(defaultValue = "0.0")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        minScore: Double,
        @RequestParam(defaultValue = "20")
        @Min(1)
        limit: Int,
        @RequestParam(defaultValue = "ALL")
        tier: MatchTier,
    ): List<ProjectMatchDto> = matchingService.findProjectsForUser(securityUser.user, minScore, limit, tier)
}
