package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.config.WebSocketSessionRegistry
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val sessionRegistry: WebSocketSessionRegistry,
    private val clock: Clock,
) {
    /** Opens a new token family. Returns the raw token — only its hash is stored. */
    fun issue(user: UserModel): String = persist(user, UUID.randomUUID().toString())

    /** Rotation stays inside the family, so later reuse of [oldToken] still revokes every sibling. */
    fun rotate(oldToken: RefreshTokenModel): String {
        oldToken.revoked = true
        return persist(oldToken.user, oldToken.familyId)
    }

    /**
     * Takes a pessimistic write lock, so concurrent refreshes of the same token serialize
     * on the caller's transaction: the loser sees `revoked = true` and hits reuse detection.
     */
    fun findByRawToken(rawToken: String): RefreshTokenModel? = refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawToken))

    /** Own transaction: reuse detection has to commit even though the caller answers 401 and rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: String) {
        refreshTokenRepository.revokeAllByFamilyId(familyId)
    }

    /**
     * Kills live STOMP sessions too. They freeze their [SecurityUser] at CONNECT and would
     * otherwise outlive the revoked token — no caller ever wants only one of the two halves.
     */
    fun revokeAllForUser(userId: String) {
        refreshTokenRepository.revokeAllUserTokens(userId)
        sessionRegistry.disconnect(userId)
    }

    private fun persist(
        user: UserModel,
        familyId: String,
    ): String {
        val rawToken = jwtService.generateOpaqueRefreshToken()
        refreshTokenRepository.save(
            RefreshTokenModel(
                tokenHash = jwtService.hashToken(rawToken),
                user = user,
                expiresAt = Instant.now(clock).plusMillis(jwtProperties.refreshTokenExpiration),
                familyId = familyId,
            ),
        )
        return rawToken
    }
}
