package org.efehan.skillmatcherbackend.core.skill

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/skills")
@Tag(name = "Skills", description = "Global skill catalog")
class SkillController(
    private val service: UserSkillService,
) {
    @Operation(
        summary = "Get all skills",
        description = "Returns all available skills from the global catalog.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Skills retrieved successfully.",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getAllSkills(
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<SkillDto> = service.getAllSkills(pageable).map(SkillModel::toDTO)
}
