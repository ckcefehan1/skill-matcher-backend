package org.efehan.skillmatcherbackend.service

import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ByteArrayResource
import java.security.Key
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date

class JwtServiceTest {
    private lateinit var jwtService: JwtService
    private lateinit var privateKey: RSAPrivateKey
    private lateinit var publicKey: RSAPublicKey
    private lateinit var fixedClock: Clock
    private lateinit var testUser: UserModel

    companion object {
        private const val ISSUER = "test-issuer"
        private const val ACCESS_TOKEN_EXPIRATION = 900_000L // 15 min
        private const val REFRESH_TOKEN_EXPIRATION = 604_800_000L // 7 days
        private val FIXED_INSTANT: Instant = Instant.parse("2025-01-01T12:00:00Z")
    }

    @BeforeEach
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        privateKey = keyPair.private as RSAPrivateKey
        publicKey = keyPair.public as RSAPublicKey

        fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)

        val jwtProperties =
            JwtProperties(
                publicKey = toPemResource(publicKey, "PUBLIC KEY"),
                privateKey = toPemResource(privateKey, "PRIVATE KEY"),
                accessTokenExpiration = ACCESS_TOKEN_EXPIRATION,
                refreshTokenExpiration = REFRESH_TOKEN_EXPIRATION,
                issuer = ISSUER,
                refreshTokenSecret = "test-hmac-secret-key",
            )

        jwtService = JwtService(jwtProperties, fixedClock)

        val role = RoleModel("ADMIN", null)
        testUser =
            UserModel(
                email = "test@example.com",
                passwordHash = "hashed",
                firstName = "Test",
                lastName = "User",
                role = role,
            )
    }

    @Test
    fun `generateAccessToken returns a valid JWT`() {
        val token = jwtService.generateAccessToken(testUser)

        assertNotNull(token)
        assertTrue(token.split(".").size == 3)
    }

    @Test
    fun `validateToken returns correct claims`() {
        val token = jwtService.generateAccessToken(testUser)
        val claims = jwtService.validateToken(token)

        assertEquals(testUser.email, claims.subject)
        assertEquals(testUser.id, claims["userId"])
        assertEquals(testUser.role.name, claims["role"])
        assertEquals(ISSUER, claims.issuer)
    }

    @Test
    fun `validateToken sets correct expiration`() {
        val token = jwtService.generateAccessToken(testUser)
        val claims = jwtService.validateToken(token)

        val expectedExpiration = Date.from(FIXED_INSTANT.plusMillis(ACCESS_TOKEN_EXPIRATION))
        assertEquals(expectedExpiration.time / 1000, claims.expiration.time / 1000)
    }

    @Test
    fun `validateToken sets correct issuedAt`() {
        val token = jwtService.generateAccessToken(testUser)
        val claims = jwtService.validateToken(token)

        val expectedIssuedAt = Date.from(FIXED_INSTANT)
        assertEquals(expectedIssuedAt.time / 1000, claims.issuedAt.time / 1000)
    }

    @Test
    fun `getEmail returns subject from token`() {
        val token = jwtService.generateAccessToken(testUser)

        assertEquals(testUser.email, jwtService.getEmail(token))
    }

    @Test
    fun `validateToken throws InvalidTokenException for expired token`() {
        val expiredClock = Clock.fixed(FIXED_INSTANT.minus(Duration.ofDays(1)), ZoneOffset.UTC)
        val expiredJwtProperties =
            JwtProperties(
                publicKey = toPemResource(publicKey, "PUBLIC KEY"),
                privateKey = toPemResource(privateKey, "PRIVATE KEY"),
                accessTokenExpiration = 1_000L, // 1 second
                refreshTokenExpiration = REFRESH_TOKEN_EXPIRATION,
                issuer = ISSUER,
                refreshTokenSecret = "test-hmac-secret-key",
            )
        val expiredService = JwtService(expiredJwtProperties, expiredClock)
        val token = expiredService.generateAccessToken(testUser)

        // Validate with current clock (1 day later) — token is expired
        assertThrows<InvalidTokenException> {
            jwtService.validateToken(token)
        }
    }

    @Test
    fun `validateToken throws InvalidTokenException for tampered token`() {
        val token = jwtService.generateAccessToken(testUser)
        val tampered = token.dropLast(5) + "XXXXX"

        assertThrows<InvalidTokenException> {
            jwtService.validateToken(tampered)
        }
    }

    @Test
    fun `validateToken throws InvalidTokenException for wrong issuer`() {
        val wrongIssuerProperties =
            JwtProperties(
                publicKey = toPemResource(publicKey, "PUBLIC KEY"),
                privateKey = toPemResource(privateKey, "PRIVATE KEY"),
                accessTokenExpiration = ACCESS_TOKEN_EXPIRATION,
                refreshTokenExpiration = REFRESH_TOKEN_EXPIRATION,
                issuer = "wrong-issuer",
                refreshTokenSecret = "test-hmac-secret-key",
            )
        val wrongIssuerService = JwtService(wrongIssuerProperties, fixedClock)
        val token = wrongIssuerService.generateAccessToken(testUser)

        assertThrows<InvalidTokenException> {
            jwtService.validateToken(token)
        }
    }

    @Test
    fun `validateToken throws InvalidTokenException for token signed with different key`() {
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val otherProperties =
            JwtProperties(
                publicKey = toPemResource(otherKeyPair.public as RSAPublicKey, "PUBLIC KEY"),
                privateKey = toPemResource(otherKeyPair.private as RSAPrivateKey, "PRIVATE KEY"),
                accessTokenExpiration = ACCESS_TOKEN_EXPIRATION,
                refreshTokenExpiration = REFRESH_TOKEN_EXPIRATION,
                issuer = ISSUER,
                refreshTokenSecret = "test-hmac-secret-key",
            )
        val otherService = JwtService(otherProperties, fixedClock)
        val token = otherService.generateAccessToken(testUser)

        assertThrows<InvalidTokenException> {
            jwtService.validateToken(token)
        }
    }

    @Test
    fun `validateToken throws InvalidTokenException for malformed token`() {
        assertThrows<InvalidTokenException> {
            jwtService.validateToken("not.a.jwt")
        }
    }

    @Test
    fun `validateToken throws for empty token`() {
        assertThrows<Exception> {
            jwtService.validateToken("")
        }
    }

    @Test
    fun `generateOpaqueRefreshToken returns 32-byte base64url token`() {
        val token = jwtService.generateOpaqueRefreshToken()

        assertNotNull(token)
        val decoded = Base64.getUrlDecoder().decode(token)
        assertEquals(32, decoded.size)
    }

    @Test
    fun `generateOpaqueRefreshToken returns unique tokens`() {
        val token1 = jwtService.generateOpaqueRefreshToken()
        val token2 = jwtService.generateOpaqueRefreshToken()

        assertNotEquals(token1, token2)
    }

    @Test
    fun `generateAccessToken produces different tokens for different users`() {
        val otherRole = RoleModel("EMPLOYER", null)
        val otherUser =
            UserModel(
                email = "other@example.com",
                passwordHash = "hashed",
                firstName = "Other",
                lastName = "User",
                role = otherRole,
            )

        val token1 = jwtService.generateAccessToken(testUser)
        val token2 = jwtService.generateAccessToken(otherUser)

        assertNotEquals(token1, token2)
    }

    @Test
    fun `token contains minimal claims only`() {
        val token = jwtService.generateAccessToken(testUser)
        val claims = jwtService.validateToken(token)

        val expectedKeys = setOf("sub", "userId", "role", "companyId", "iss", "iat", "exp")
        assertEquals(expectedKeys, claims.keys)
    }

    private fun toPemResource(
        key: Key,
        type: String,
    ): ByteArrayResource {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded)
        val pem = "-----BEGIN $type-----\n$encoded\n-----END $type-----\n"
        return ByteArrayResource(pem.toByteArray())
    }
}
