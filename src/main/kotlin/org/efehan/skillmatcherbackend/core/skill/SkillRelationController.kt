package org.efehan.skillmatcherbackend.core.skill

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Skill Relations", description = "Curated and learned relations between skills")
class SkillRelationController(
    private val service: SkillRelationService,
) {
    @Operation(
        summary = "Create a curated skill relation",
        description = "Creates a curated relation between two skills (admin only).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Relation created.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = SkillRelationDto::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation error.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Admin role required.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Skill not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Relation already exists.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @PostMapping("/api/admin/skill-relations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun create(
        @Valid @RequestBody request: CreateSkillRelationRequest,
    ): SkillRelationDto =
        service
            .create(
                fromSkillId = request.fromSkillId,
                toSkillId = request.toSkillId,
                relationType = request.relationType,
                transferPenalty = request.transferPenalty,
            ).toDTO()

    @Operation(
        summary = "Delete a skill relation",
        description = "Deletes a skill relation by id (admin only).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Relation deleted.", content = [Content()]),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Admin role required.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Relation not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @DeleteMapping("/api/admin/skill-relations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(
        @PathVariable id: String,
    ) {
        service.delete(id)
    }

    @Operation(
        summary = "List relations for a skill",
        description = "Returns all relations (curated and learned) involving the given skill.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Relations retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Skill not found.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping("/api/skill-relations")
    @ResponseStatus(HttpStatus.OK)
    fun listBySkill(
        @RequestParam skillId: String,
    ): List<SkillRelationDto> = service.listBySkill(skillId).map { it.toDTO() }
}
