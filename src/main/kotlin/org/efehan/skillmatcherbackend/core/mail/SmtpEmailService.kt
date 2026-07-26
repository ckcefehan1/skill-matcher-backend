package org.efehan.skillmatcherbackend.core.mail

import org.efehan.skillmatcherbackend.config.properties.MailProperties
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["mail.smtp.enabled"], havingValue = "true")
class SmtpEmailService(
    private val javaMailSender: JavaMailSender,
    private val templateService: TemplateService,
    private val mailProperties: MailProperties,
) : EmailService {
    private val logger = LoggerFactory.getLogger(SmtpEmailService::class.java)

    @Async
    override fun sendInvitationEmail(
        user: UserModel,
        invitationToken: String,
        expirationHours: Long,
    ) {
        try {
            val invitationLink = "${mailProperties.baseUrl}/invitations/accept?token=$invitationToken"
            val htmlContent =
                templateService.renderInvitation(
                    firstName = user.firstName ?: "User",
                    invitationLink = invitationLink,
                    expirationHours = expirationHours,
                )
            send(user.email, "You have been invited to Skill Matcher", htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send invitation email to={}", user.email, ex)
        }
    }

    @Async
    override fun sendWelcomeEmail(user: UserModel) {
        try {
            val htmlContent = templateService.renderWelcome(firstName = user.firstName ?: "User")
            send(user.email, "Welcome to Skill Matcher", htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send welcome email to={}", user.email, ex)
        }
    }

    @Async
    override fun sendPasswordResetEmail(
        user: UserModel,
        resetToken: String,
        expirationHours: Long,
    ) {
        try {
            val resetLink = "${mailProperties.baseUrl}/password-reset/confirm?token=$resetToken"
            val htmlContent =
                templateService.renderPasswordReset(
                    firstName = user.firstName ?: "User",
                    resetLink = resetLink,
                    expirationHours = expirationHours,
                )
            send(user.email, "Reset Your Password - Skill Matcher", htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send password reset email to={}", user.email, ex)
        }
    }

    @Async
    override fun sendApplicationSubmittedEmail(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        try {
            val htmlContent = templateService.renderApplicationSubmitted(pm, applicant, project, message)
            send(pm.email, "New application for '${project.name}'", htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send application-submitted email to pm={}", pm.email, ex)
        }
    }

    @Async
    override fun sendApplicationDecidedEmail(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    ) {
        try {
            val htmlContent = templateService.renderApplicationDecided(applicant, project, accepted, reason)
            val subject =
                if (accepted) {
                    "Your application for '${project.name}' was accepted"
                } else {
                    "Your application for '${project.name}' was declined"
                }
            send(applicant.email, subject, htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send application-decided email to={}", applicant.email, ex)
        }
    }

    @Async
    override fun sendProjectInvitationEmail(
        invitee: UserModel,
        pm: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        try {
            val htmlContent = templateService.renderProjectInvitation(invitee, pm, project, message)
            send(invitee.email, "You have been invited to join '${project.name}'", htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send project-invitation email to={}", invitee.email, ex)
        }
    }

    @Async
    override fun sendProjectInvitationResponseEmail(
        pm: UserModel,
        employer: UserModel,
        project: ProjectModel,
        accepted: Boolean,
    ) {
        try {
            val htmlContent = templateService.renderProjectInvitationResponse(pm, employer, project, accepted)
            val subject =
                if (accepted) {
                    "Your invitation for '${project.name}' was accepted"
                } else {
                    "Your invitation for '${project.name}' was declined"
                }
            send(pm.email, subject, htmlContent)
        } catch (ex: Exception) {
            logger.error("Failed to send project-invitation-response email to pm={}", pm.email, ex)
        }
    }

    private fun send(
        to: String,
        subject: String,
        htmlBody: String,
    ) {
        val message = javaMailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(mailProperties.from)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(htmlBody, true)

        javaMailSender.send(message)
        logger.info("Email sent to={} subject={}", to, subject)
    }
}
