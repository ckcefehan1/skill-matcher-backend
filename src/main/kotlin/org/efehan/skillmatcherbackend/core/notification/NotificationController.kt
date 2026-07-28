package org.efehan.skillmatcherbackend.core.notification

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.efehan.skillmatcherbackend.persistence.NotificationModel
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @Operation(summary = "Get my notifications", description = "Returns the newest notifications for the authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Notifications retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getNotifications(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @RequestParam(defaultValue = "30") limit: Int,
    ): List<NotificationResponse> =
        notificationService
            .list(securityUser.user, limit)
            .map(NotificationModel::toDTO)

    @Operation(summary = "Get unread notification count")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Count retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [Content(schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @GetMapping("/unread-count")
    @ResponseStatus(HttpStatus.OK)
    fun getUnreadCount(
        @AuthenticationPrincipal securityUser: SecurityUser,
    ): UnreadCountResponse = UnreadCountResponse(notificationService.unreadCount(securityUser.user))

    @Operation(summary = "Mark notification as read")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Notification marked as read."),
            ApiResponse(
                responseCode = "404",
                description = "Notification not found.",
                content = [Content(schema = Schema(implementation = GlobalErrorCodeResponse::class))],
            ),
        ],
    )
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
    ) {
        notificationService.markRead(securityUser.user, id)
    }

    @Operation(summary = "Mark all notifications as read")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "All notifications marked as read."),
        ],
    )
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(
        @AuthenticationPrincipal securityUser: SecurityUser,
    ) {
        notificationService.markAllRead(securityUser.user)
    }
}
