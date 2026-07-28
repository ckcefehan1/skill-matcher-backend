package org.efehan.skillmatcherbackend.core.invitation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.core.auth.AuthCookieService
import org.efehan.skillmatcherbackend.core.auth.AuthResponse
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/invitations")
@Tag(name = "Invitation", description = "Invitation endpoints")
class InvitationController(
    private val invitationService: InvitationService,
    private val authCookieService: AuthCookieService,
) {
    @Operation(
        summary = "Validate invitation token",
        method = "POST",
        description =
            "Validates an invitation token without accepting it. " +
                "Use this to check if a token is valid before showing the password form.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Token is valid.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ValidateInvitationResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Valid token",
                                value = """
                                {
                                    "valid": true,
                                    "email": "max.mustermann@firma.de"
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid, expired, or already used invitation token.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Invalid token",
                                value = """
                                {
                                    "errorCode": "INVALID_INVITATION_TOKEN",
                                    "errorMessage": "Invitation token is invalid.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                            ExampleObject(
                                name = "Token expired",
                                value = """
                                {
                                    "errorCode": "INVITATION_TOKEN_EXPIRED",
                                    "errorMessage": "Invitation token has expired.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                            ExampleObject(
                                name = "Already accepted",
                                value = """
                                {
                                    "errorCode": "INVITATION_ALREADY_ACCEPTED",
                                    "errorMessage": "Invitation has already been accepted.",
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
    @PostMapping("/validate")
    @ResponseStatus(HttpStatus.OK)
    fun validateInvitation(
        @Valid
        @RequestBody
        request: ValidateInvitationRequest,
    ): ValidateInvitationResponse = invitationService.validateInvitation(request.token).toDTO()

    @Operation(
        summary = "Accept invitation",
        method = "POST",
        description =
            "Accepts an invitation token, sets the user's password and profile (firstName, lastName). " +
                "Sets httpOnly access_token and refresh_token cookies.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Invitation accepted successfully.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AuthResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Invitation accepted",
                                value = """
                                {
                                    "expiresIn": 900000,
                                    "user": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "email": "max.mustermann@firma.de",
                                        "firstName": "Max",
                                        "lastName": "Mustermann",
                                        "role": "EMPLOYER"
                                    }
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid, expired, or already used invitation token.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Invalid token",
                                value = """
                                {
                                    "errorCode": "INVALID_INVITATION_TOKEN",
                                    "errorMessage": "Invitation token is invalid.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                            ExampleObject(
                                name = "Token expired",
                                value = """
                                {
                                    "errorCode": "INVITATION_TOKEN_EXPIRED",
                                    "errorMessage": "Invitation token has expired.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                            ExampleObject(
                                name = "Already accepted",
                                value = """
                                {
                                    "errorCode": "INVITATION_ALREADY_ACCEPTED",
                                    "errorMessage": "Invitation has already been accepted.",
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
    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.OK)
    fun acceptInvitation(
        @Valid
        @RequestBody
        request: AcceptInvitationRequest,
        response: HttpServletResponse,
    ): AuthResponse {
        val tokens =
            invitationService.acceptInvitation(
                request.token,
                request.password,
                request.firstName,
                request.lastName,
            )
        authCookieService.addCookies(response, tokens.accessToken, tokens.refreshToken)
        return tokens.response
    }
}
