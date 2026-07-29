package org.efehan.skillmatcherbackend.core.audit

import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.AuditLogModel
import org.efehan.skillmatcherbackend.persistence.AuditLogRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuditService(
    private val auditLogRepository: AuditLogRepository,
) {
    /**
     * Joins the caller's transaction on purpose: an entry for an admin action that then
     * rolled back would be worse than a missing one. Events that are recorded on a path
     * which throws — a failed login — have to call this from their own transaction.
     */
    fun record(
        action: AuditAction,
        actor: UserModel? = currentUser(),
        targetId: String? = null,
        detail: String? = null,
    ) {
        auditLogRepository.save(
            AuditLogModel(
                action = action,
                actorId = actor?.id,
                actorEmail = actor?.email,
                targetId = targetId,
                detail = detail,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun list(pageable: Pageable): Page<AuditLogModel> = auditLogRepository.findAllByOrderByCreatedDateDesc(pageable)

    private fun currentUser(): UserModel? = (SecurityContextHolder.getContext().authentication?.principal as? SecurityUser)?.user
}
