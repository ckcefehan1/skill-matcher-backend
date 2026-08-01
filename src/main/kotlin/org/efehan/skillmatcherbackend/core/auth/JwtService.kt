package org.efehan.skillmatcherbackend.core.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class JwtService(
    private val jwtProperties: JwtProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val privateKey: RSAPrivateKey by lazy { loadPrivateKey() }
    private val publicKey: RSAPublicKey by lazy { loadPublicKey() }
    private val secureRandom = SecureRandom()

    private val jwtParser: JwtParser by lazy {
        Jwts
            .parser()
            .verifyWith(publicKey)
            .requireIssuer(jwtProperties.issuer)
            .clock { Date.from(clock.instant()) }
            .build()
    }

    fun generateAccessToken(user: UserModel): String {
        val now = clock.instant()
        // SUPERADMIN tokens carry no companyId claim on purpose: missing claim = root context.
        // The row itself still has one, because users.company_id is NOT NULL.
        val companyId = user.companyId.takeUnless { user.role.name == RoleName.SUPERADMIN.name }
        return Jwts
            .builder()
            .subject(user.email)
            .claim("userId", user.id)
            .claim("role", user.role.name)
            .claim("companyId", companyId)
            .issuer(jwtProperties.issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(jwtProperties.accessTokenExpiration)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }

    private val hmacKey =
        SecretKeySpec(
            jwtProperties.refreshTokenSecret.toByteArray(StandardCharsets.UTF_8),
            "HmacSHA256",
        )

    fun generateOpaqueRefreshToken(): String {
        val bytes = ByteArray(32) // 256 bit
        secureRandom.nextBytes(bytes)
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun hashToken(token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        val raw =
            mac.doFinal(
                token.toByteArray(StandardCharsets.UTF_8),
            )
        return Base64.getEncoder().encodeToString(raw)
    }

    fun validateToken(token: String): Claims =
        try {
            jwtParser.parseSignedClaims(token).payload
        } catch (ex: JwtException) {
            throw InvalidTokenException(
                message = "Invalid JWT",
                cause = ex,
                errorCode = GlobalErrorCode.INVALID_REFRESH_TOKEN,
                status = HttpStatus.UNAUTHORIZED,
            )
        } catch (ex: IllegalArgumentException) {
            throw InvalidTokenException(
                message = "Invalid JWT",
                cause = ex,
                errorCode = GlobalErrorCode.INVALID_REFRESH_TOKEN,
                status = HttpStatus.UNAUTHORIZED,
            )
        }

    fun getEmail(token: String): String = validateToken(token).subject

    private fun loadPrivateKey(): RSAPrivateKey {
        val keyBytes =
            jwtProperties.privateKey.inputStream.use { stream ->
                String(stream.readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), "")
                    .let { Base64.getDecoder().decode(it) }
            }
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
    }

    private fun loadPublicKey(): RSAPublicKey {
        val keyBytes =
            jwtProperties.publicKey.inputStream.use { stream ->
                String(stream.readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\\s".toRegex(), "")
                    .let { Base64.getDecoder().decode(it) }
            }
        val keySpec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
    }
}
