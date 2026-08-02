package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.WebSocketSessionRegistry
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.RefreshTokenService
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockKExtension::class)
@DisplayName("Refresh Token Service Unit Tests")
class RefreshTokenServiceTest {
    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var jwtService: JwtService

    @MockK
    private lateinit var jwtProperties: JwtProperties

    @MockK(relaxed = true)
    private lateinit var sessionRegistry: WebSocketSessionRegistry

    @MockK
    private lateinit var clock: Clock

    @InjectMockKs
    private lateinit var refreshTokenService: RefreshTokenService

    companion object {
        private const val RAW_TOKEN = "refresh-token-uuid"
        private const val TOKEN_HASH = "hashed-refresh-token"
        private const val REFRESH_TOKEN_EXPIRATION = 604_800_000L
        private val FIXED_INSTANT: Instant = Instant.parse("2025-01-01T12:00:00Z")
    }

    private fun user() = UserBuilder().build(email = "test@example.com", role = RoleModel("ADMIN", null))

    @BeforeEach
    fun setUp() {
        every { clock.instant() } returns FIXED_INSTANT
        every { jwtProperties.refreshTokenExpiration } returns REFRESH_TOKEN_EXPIRATION
    }

    @Test
    fun `issue stores only the hash and opens a fresh family`() {
        // given
        val user = user()
        val tokenSlot = slot<RefreshTokenModel>()
        every { jwtService.generateOpaqueRefreshToken() } returns RAW_TOKEN
        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { refreshTokenRepository.save(capture(tokenSlot)) } returnsArgument 0

        // when
        val result = refreshTokenService.issue(user)

        // then
        assertThat(result).isEqualTo(RAW_TOKEN)
        val saved = tokenSlot.captured
        assertThat(saved.tokenHash).isEqualTo(TOKEN_HASH)
        assertThat(saved.user).isEqualTo(user)
        assertThat(saved.expiresAt).isEqualTo(FIXED_INSTANT.plusMillis(REFRESH_TOKEN_EXPIRATION))
        assertThat(saved.familyId).isNotBlank()
        assertThat(saved.revoked).isFalse()
    }

    @Test
    fun `rotate revokes the old token and keeps the family`() {
        // given
        val user = user()
        val newRawToken = "new-refresh-token-uuid"
        val oldToken =
            RefreshTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(7, ChronoUnit.DAYS),
                familyId = "family-1",
            )
        val tokenSlot = slot<RefreshTokenModel>()
        every { jwtService.generateOpaqueRefreshToken() } returns newRawToken
        every { jwtService.hashToken(newRawToken) } returns "new-hashed-refresh-token"
        every { refreshTokenRepository.save(capture(tokenSlot)) } returnsArgument 0

        // when
        val result = refreshTokenService.rotate(oldToken)

        // then
        assertThat(result).isEqualTo(newRawToken)
        assertThat(oldToken.revoked).isTrue()
        val saved = tokenSlot.captured
        assertThat(saved.tokenHash).isEqualTo("new-hashed-refresh-token")
        assertThat(saved.familyId).isEqualTo("family-1")
        assertThat(saved.expiresAt).isEqualTo(FIXED_INSTANT.plusMillis(REFRESH_TOKEN_EXPIRATION))
        assertThat(saved.revoked).isFalse()
    }

    @Test
    fun `findByRawToken looks the row up by hash`() {
        // given
        val stored =
            RefreshTokenModel(
                tokenHash = TOKEN_HASH,
                user = user(),
                expiresAt = FIXED_INSTANT.plus(7, ChronoUnit.DAYS),
                familyId = "family-1",
            )
        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { refreshTokenRepository.findByTokenHash(TOKEN_HASH) } returns stored

        // then
        assertThat(refreshTokenService.findByRawToken(RAW_TOKEN)).isEqualTo(stored)
    }

    @Test
    fun `revokeAllForUser also tears down live websocket sessions`() {
        // given
        val userId = "user-id-123"
        every { refreshTokenRepository.revokeAllUserTokens(userId) } returns 3

        // when
        refreshTokenService.revokeAllForUser(userId)

        // then
        verify(exactly = 1) { refreshTokenRepository.revokeAllUserTokens(userId) }
        verify(exactly = 1) { sessionRegistry.disconnect(userId) }
    }

    @Test
    fun `revokeFamily revokes every sibling token`() {
        // given
        every { refreshTokenRepository.revokeAllByFamilyId("family-1") } returns 2

        // when
        refreshTokenService.revokeFamily("family-1")

        // then
        verify(exactly = 1) { refreshTokenRepository.revokeAllByFamilyId("family-1") }
    }
}
