package org.efehan.skillmatcherbackend.core.mail

import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["mail.smtp.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingEmailService : EmailService {
    private val logger = LoggerFactory.getLogger(LoggingEmailService::class.java)

    override fun sendInvitationEmail(
        user: UserModel,
        invitationToken: String,
        expirationHours: Long,
    ) {
        logger.info("Mock sending invitation email to {} with token {} (expires in {}h)", user.email, invitationToken, expirationHours)
    }

    override fun sendWelcomeEmail(user: UserModel) {
        logger.info("Mock sending welcome email to {}", user.email)
    }

    override fun sendPasswordResetEmail(
        user: UserModel,
        resetToken: String,
        expirationHours: Long,
    ) {
        logger.info("Mock sending password reset email to {} with token {} (expires in {}h)", user.email, resetToken, expirationHours)
    }

    override fun sendApplicationSubmittedEmail(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        logger.info(
            "Mock sending application-submitted email to pm={} for project={} from applicant={} message={}",
            pm.email,
            project.name,
            applicant.email,
            message,
        )
    }

    override fun sendApplicationDecidedEmail(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    ) {
        logger.info(
            "Mock sending application-decided email to {} for project={} accepted={} reason={}",
            applicant.email,
            project.name,
            accepted,
            reason,
        )
    }
}
