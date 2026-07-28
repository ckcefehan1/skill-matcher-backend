package org.efehan.skillmatcherbackend.core.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
class AuthenticationController(
    private val authenticationService: AuthenticationService,
    private val authCookieService: AuthCookieService,
) {
    @Operation(
        summary = "Bootstrap CSRF token",
        method = "GET",
        description = "Safe endpoint that forces the XSRF-TOKEN cookie to be written. Call once before the first mutating request.",
    )
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun csrf() {
        // empty: CsrfFilter resolves the token, CookieCsrfTokenRepository writes the cookie
    }

    @Operation(
        summary = "Login user",
        method = "POST",
        description =
            "Authenticates a user and sets httpOnly access_token and refresh_token cookies. " +
                "Requires the X-XSRF-TOKEN header.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Login successful.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AuthResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Successful login",
                                value = """
                                {
                                    "expiresIn": 900000,
                                    "user": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "email": "user@example.com",
                                        "firstName": "John",
                                        "lastName": "Doe",
                                        "role": "ADMIN"
                                    }
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Invalid credentials.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Bad credentials",
                                value = """
                                {
                                    "errorCode": "BAD_CREDENTIALS",
                                    "errorMessage": "Bad credentials.",
                                    "fieldErrors": []
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
                                            "field": "email",
                                            "message": "email must be a valid email address"
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
                responseCode = "403",
                description = "Account disabled.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Account disabled",
                                value = """
                                {
                                    "errorCode": "ACCOUNT_DISABLED",
                                    "errorMessage": "Account is disabled.",
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
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    fun login(
        @Valid
        @RequestBody
        request: LoginRequest,
        response: HttpServletResponse,
    ): AuthResponse {
        val tokens = authenticationService.login(request.email, request.password)
        authCookieService.addCookies(response, tokens.accessToken, tokens.refreshToken)
        return tokens.response
    }

    @Operation(
        summary = "Refresh access token",
        method = "POST",
        description =
            "Uses the refresh_token cookie to issue a new access token. " +
                "The refresh token is rotated on every use; reuse of a rotated token revokes the whole token family.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Token refreshed successfully.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AuthResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Successful refresh",
                                value = """
                                {
                                    "expiresIn": 900000,
                                    "user": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "email": "user@example.com",
                                        "firstName": "John",
                                        "lastName": "Doe",
                                        "role": "ADMIN"
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
                description = "Refresh token not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Token not found",
                                value = """
                                {
                                    "errorCode": "REFRESH_TOKEN_NOT_FOUND",
                                    "errorMessage": "RefreshToken with token not found.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Refresh token expired or invalid.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Token expired or invalid",
                                value = """
                                {
                                    "errorCode": "INVALID_REFRESH_TOKEN",
                                    "errorMessage": "Refresh token is expired or invalid.",
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
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    fun refreshToken(
        @CookieValue("\${cookie.refresh-token-name:refresh_token}", required = false)
        rawToken: String?,
        response: HttpServletResponse,
    ): AuthResponse {
        if (rawToken == null) {
            throw InvalidTokenException(
                message = "Refresh token cookie is missing",
                errorCode = GlobalErrorCode.INVALID_REFRESH_TOKEN,
                status = HttpStatus.UNAUTHORIZED,
            )
        }
        val tokens = authenticationService.refreshToken(rawToken)
        authCookieService.addCookies(response, tokens.accessToken, tokens.refreshToken)
        return tokens.response
    }

    @Operation(
        summary = "Logout user",
        method = "POST",
        description = "Revokes all refresh tokens for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Logout successful.",
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
        ],
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @AuthenticationPrincipal securityUser: SecurityUser,
        response: HttpServletResponse,
    ) {
        authenticationService.logout(securityUser.user.id)
        authCookieService.clearCookies(response)
    }

    @Operation(
        summary = "Change password",
        method = "POST",
        description = "Changes the password for the authenticated user. Revokes all refresh tokens.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Password changed successfully.",
                content = [Content()],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Current password is incorrect.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Wrong password",
                                value = """
                                {
                                    "errorCode": "BAD_CREDENTIALS",
                                    "errorMessage": "Bad credentials.",
                                    "fieldErrors": []
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "New password does not meet requirements.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Password validation error",
                                value = """
                                {
                                    "errorCode": "VALIDATION_ERROR",
                                    "errorMessage": "Password does not meet the required complexity.",
                                    "fieldErrors": [
                                        {
                                            "field": "password",
                                            "message": "Password must be at least 8 characters long"
                                        }
                                    ]
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid
        @RequestBody
        request: ChangePasswordRequest,
    ) {
        authenticationService.changePassword(
            user = securityUser.user,
            currentPassword = request.oldPassword,
            newPassword = request.newPassword,
        )
    }
}
