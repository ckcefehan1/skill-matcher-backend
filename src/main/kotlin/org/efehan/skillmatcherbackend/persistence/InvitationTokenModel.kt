package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.invitation.ValidateInvitationResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity
@Table(
    name = "invitation_tokens",
    indexes = [
        Index(
            name = "idx_invitation_tokens_token_hash",
            columnList = "token_hash",
        ),
    ],
)
class InvitationTokenModel(
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserModel,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "used", nullable = false)
    var used: Boolean = false,
    // HMAC of the 6-digit self-registration code. Null on employee invitations —
    // that null is what distinguishes the two row types.
    @Column(name = "code_hash")
    var codeHash: String? = null,
    // failed verify attempts against this row, capped in InvitationService
    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,
) : TenantAwareEntity() {
    fun toDTO() =
        ValidateInvitationResponse(
            valid = true,
            email = user.email,
        )
}

@Repository
interface InvitationTokenRepository : JpaRepository<InvitationTokenModel, String> {
    fun findByTokenHash(tokenHash: String): InvitationTokenModel?

    fun findFirstByUserAndCodeHashNotNullOrderByCreatedDateDesc(user: UserModel): InvitationTokenModel?

    fun deleteByUser(user: UserModel)
}
