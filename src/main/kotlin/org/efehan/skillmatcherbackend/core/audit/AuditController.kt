package org.efehan.skillmatcherbackend.core.audit

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/audit-logs")
@Tag(name = "Admin", description = "Admin endpoints")
class AuditController(
    private val auditService: AuditService,
) {
    @Operation(
        summary = "List audit log entries",
        method = "GET",
        description = "Returns security relevant events, newest first. Only accessible by admins.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Audit log retrieved successfully.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AuditLogResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Audit log",
                                value = """
                                [
                                    {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "action": "USER_ROLE_CHANGED",
                                        "actorId": "9f1c2f3e-1111-2222-3333-444455556666",
                                        "actorEmail": "admin@firma.de",
                                        "targetId": "1a2b3c4d-5555-6666-7777-888899990000",
                                        "detail": "EMPLOYEE -> EMPLOYER",
                                        "createdDate": "2026-02-18T12:00:00Z"
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
                                    "errorCode": "UNAUTHORIZED",
                                    "errorMessage": "Not authenticated.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not authorized. Admin role required.",
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
                                    "errorMessage": "Access denied.",
                                    "fieldErrors": []
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
    fun listAuditLogs(
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<AuditLogResponse> = auditService.list(pageable).map { it.toDTO() }
}
