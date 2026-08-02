package org.efehan.skillmatcherbackend.core.user

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class UserSearchResultDto(
    val id: String,
    val userName: String,
    val email: String,
)

@RestController
@Tag(name = "User Search", description = "Search users by name or email")
class UserSearchController(
    private val userService: UserService,
) {
    @Operation(
        summary = "Search employer users",
        description = "Returns enabled EMPLOYER users matching the query in email, first or last name. PROJECTMANAGER only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not PROJECTMANAGER role.",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping("/api/users/search")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('PROJECTMANAGER')")
    fun searchUsers(
        @RequestParam(required = false) q: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<UserSearchResultDto> =
        userService.searchByRole("EMPLOYER", q ?: "", pageable).map {
            UserSearchResultDto(
                id = it.id,
                userName = "${it.firstName ?: ""} ${it.lastName ?: ""}".trim(),
                email = it.email,
            )
        }
}
