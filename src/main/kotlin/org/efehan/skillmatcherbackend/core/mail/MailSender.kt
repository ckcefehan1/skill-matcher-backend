package org.efehan.skillmatcherbackend.core.mail

import org.efehan.skillmatcherbackend.config.properties.MailProperties
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * Raw synchronous mail sender. Throws on failure so callers (e.g. the RabbitMQ
 * consumer) can decide on retries. Wrap with an @Async facade for fire-and-forget.
 */
@Component("appMailSender")
@ConditionalOnProperty(name = ["mail.smtp.enabled"], havingValue = "true")
class MailSender(
    private val javaMailSender: JavaMailSender,
    private val templateService: TemplateService,
    private val mailProperties: MailProperties,
) {
    private val logger = LoggerFactory.getLogger(MailSender::class.java)

    fun sendInvitationEmail(
        user: UserModel,
        invitationToken: String,
        expirationHours: Long,
    ) {
        val invitationLink = "${mailProperties.baseUrl}/invitations/accept?token=$invitationToken"
        val htmlContent =
            templateService.renderInvitation(
                firstName = user.firstName ?: "User",
                invitationLink = invitationLink,
                expirationHours = expirationHours,
            )
        send(user.email, "You have been invited to Skill Matcher", htmlContent)
    }

    fun sendRegistrationCodeEmail(
        user: UserModel,
        code: String,
        expirationMinutes: Long,
    ) {
        val htmlContent =
            templateService.renderRegistrationCode(
                code = code,
                expirationMinutes = expirationMinutes,
            )
        send(user.email, "Your Skill Matcher registration code", htmlContent)
    }

    fun sendWelcomeEmail(user: UserModel) {
        val htmlContent = templateService.renderWelcome(firstName = user.firstName ?: "User")
        send(user.email, "Welcome to Skill Matcher", htmlContent)
    }

    fun sendPasswordResetEmail(
        user: UserModel,
        resetToken: String,
        expirationHours: Long,
    ) {
        val resetLink = "${mailProperties.baseUrl}/password-reset/confirm?token=$resetToken"
        val htmlContent =
            templateService.renderPasswordReset(
                firstName = user.firstName ?: "User",
                resetLink = resetLink,
                expirationHours = expirationHours,
            )
        send(user.email, "Reset Your Password - Skill Matcher", htmlContent)
    }

    fun sendApplicationSubmittedEmail(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        val htmlContent = templateService.renderApplicationSubmitted(pm, applicant, project, message)
        send(pm.email, "New application for '${project.name}'", htmlContent)
    }

    fun sendApplicationDecidedEmail(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    ) {
        val htmlContent = templateService.renderApplicationDecided(applicant, project, accepted, reason)
        val subject =
            if (accepted) {
                "Your application for '${project.name}' was accepted"
            } else {
                "Your application for '${project.name}' was declined"
            }
        send(applicant.email, subject, htmlContent)
    }

    fun sendProjectInvitationEmail(
        invitee: UserModel,
        pm: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        val htmlContent = templateService.renderProjectInvitation(invitee, pm, project, message)
        send(invitee.email, "You have been invited to join '${project.name}'", htmlContent)
    }

    fun sendProjectInvitationResponseEmail(
        pm: UserModel,
        employer: UserModel,
        project: ProjectModel,
        accepted: Boolean,
    ) {
        val htmlContent = templateService.renderProjectInvitationResponse(pm, employer, project, accepted)
        val subject =
            if (accepted) {
                "Your invitation for '${project.name}' was accepted"
            } else {
                "Your invitation for '${project.name}' was declined"
            }
        send(pm.email, subject, htmlContent)
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
