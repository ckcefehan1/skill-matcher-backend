package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.audit.AuditLogResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

enum class AuditAction {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    PASSWORD_CHANGED,
    USER_CREATED,
    USER_ENABLED,
    USER_DISABLED,
    USER_ROLE_CHANGED,
    APPLICATION_ACCEPTED,
    APPLICATION_DECLINED,
}

/**
 * The actor is denormalized instead of joined: an audit trail has to stay readable
 * after the account it describes is gone, and account deletion is coming with GDPR.
 */
@Entity
@Table(name = "audit_logs")
class AuditLogModel(
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    val action: AuditAction,
    @Column(name = "actor_id")
    val actorId: String?,
    @Column(name = "actor_email")
    val actorEmail: String?,
    @Column(name = "target_id")
    val targetId: String?,
    @Column(name = "detail")
    val detail: String?,
    // nullable root context: SUPERADMIN actions have no tenant, no @TenantId filter here
    @Column(name = "company_id")
    val companyId: String? = null,
) : AuditingBaseEntity() {
    fun toDTO() =
        AuditLogResponse(
            id = id,
            action = action.name,
            actorId = actorId,
            actorEmail = actorEmail,
            targetId = targetId,
            detail = detail,
            createdDate = createdDate!!,
        )
}

@Repository
interface AuditLogRepository : JpaRepository<AuditLogModel, String> {
    fun findAllByOrderByCreatedDateDesc(pageable: Pageable): Page<AuditLogModel>
}
