package org.efehan.skillmatcherbackend.core.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "email must not be blank")
    @field:Email(message = "email must be a valid email address")
    val email: String,
    @field:NotBlank(message = "password must be required")
    val password: String,
)

data class AuthResponse(
    val expiresIn: Long = 900000, // 15 min
    val user: AuthUserResponse,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val response: AuthResponse,
)

data class AuthUserResponse(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String,
)

data class WsTicketResponse(
    val ticket: String,
    val expiresInSeconds: Long,
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "oldPassword must not be blank")
    val oldPassword: String,
    @field:NotBlank(message = "newPassword must not be blank")
    val newPassword: String,
)
