package org.efehan.skillmatcherbackend.core.mail

import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Fire-and-forget email facade used when RabbitMQ is disabled. Failures are
 * logged and swallowed so mail problems never break the calling request.
 */
@Service
@ConditionalOnProperty(name = ["mail.smtp.enabled"], havingValue = "true")
class SmtpEmailService(
    private val mailSender: MailSender,
) : EmailService {
    private val logger = LoggerFactory.getLogger(SmtpEmailService::class.java)

    @Async
    override fun sendInvitationEmail(
        user: UserModel,
        invitationToken: String,
        expirationHours: Long,
    ) {
        runCatching { mailSender.sendInvitationEmail(user, invitationToken, expirationHours) }
            .onFailure { logger.error("Failed to send invitation email to={}", user.email, it) }
    }

    @Async
    override fun sendRegistrationCodeEmail(
        user: UserModel,
        code: String,
        expirationMinutes: Long,
    ) {
        runCatching { mailSender.sendRegistrationCodeEmail(user, code, expirationMinutes) }
            .onFailure { logger.error("Failed to send registration code email to={}", user.email, it) }
    }

    @Async
    override fun sendWelcomeEmail(user: UserModel) {
        runCatching { mailSender.sendWelcomeEmail(user) }
            .onFailure { logger.error("Failed to send welcome email to={}", user.email, it) }
    }

    @Async
    override fun sendPasswordResetEmail(
        user: UserModel,
        resetToken: String,
        expirationHours: Long,
    ) {
        runCatching { mailSender.sendPasswordResetEmail(user, resetToken, expirationHours) }
            .onFailure { logger.error("Failed to send password reset email to={}", user.email, it) }
    }

    @Async
    override fun sendApplicationSubmittedEmail(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        runCatching { mailSender.sendApplicationSubmittedEmail(pm, applicant, project, message) }
            .onFailure { logger.error("Failed to send application-submitted email to pm={}", pm.email, it) }
    }

    @Async
    override fun sendApplicationDecidedEmail(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    ) {
        runCatching { mailSender.sendApplicationDecidedEmail(applicant, project, accepted, reason) }
            .onFailure { logger.error("Failed to send application-decided email to={}", applicant.email, it) }
    }

    @Async
    override fun sendProjectInvitationEmail(
        invitee: UserModel,
        pm: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        runCatching { mailSender.sendProjectInvitationEmail(invitee, pm, project, message) }
            .onFailure { logger.error("Failed to send project-invitation email to={}", invitee.email, it) }
    }

    @Async
    override fun sendProjectInvitationResponseEmail(
        pm: UserModel,
        employer: UserModel,
        project: ProjectModel,
        accepted: Boolean,
    ) {
        runCatching { mailSender.sendProjectInvitationResponseEmail(pm, employer, project, accepted) }
            .onFailure { logger.error("Failed to send project-invitation-response email to pm={}", pm.email, it) }
    }
}
