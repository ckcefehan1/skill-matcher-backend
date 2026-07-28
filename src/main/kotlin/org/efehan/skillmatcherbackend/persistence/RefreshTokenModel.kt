package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.LockModeType
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenModel(
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserModel,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "family_id", nullable = false)
    val familyId: String,
    @Column(name = "revoked", nullable = false)
    var revoked: Boolean = false,
) : AuditingBaseEntity()

interface RefreshTokenRepository : JpaRepository<RefreshTokenModel, String> {
    // serializes concurrent refreshes of the same token: second tx blocks here,
    // then sees revoked=true and hits reuse detection
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHash(tokenHash: String): RefreshTokenModel?

    @Modifying
    @Query(
        value =
            "UPDATE RefreshTokenModel rt " +
                "SET rt.revoked = true " +
                "WHERE rt.user.id = :userId",
    )
    fun revokeAllUserTokens(userId: String): Int

    // revoked=false filter: reuse detection runs in REQUIRES_NEW while the outer tx
    // holds a pessimistic lock on the (already revoked) row — touching it would deadlock
    @Modifying
    @Query(
        value =
            "UPDATE RefreshTokenModel rt " +
                "SET rt.revoked = true " +
                "WHERE rt.familyId = :familyId AND rt.revoked = false",
    )
    fun revokeAllByFamilyId(familyId: String): Int
}
