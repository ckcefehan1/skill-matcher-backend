package org.efehan.skillmatcherbackend.core.mail.rabbit

import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Primary
@ConditionalOnProperty(name = ["mail.rabbitmq.enabled"], havingValue = "true")
class RabbitEmailService(
    private val rabbitTemplate: RabbitTemplate,
    private val properties: RabbitMQProperties,
) : EmailService {
    override fun sendInvitationEmail(
        user: UserModel,
        invitationToken: String,
        expirationHours: Long,
    ) {
        publishAfterCommit(MailCommand.Invitation(user.id, invitationToken, expirationHours))
    }

    override fun sendRegistrationCodeEmail(
        user: UserModel,
        code: String,
        expirationMinutes: Long,
    ) {
        publishAfterCommit(MailCommand.RegistrationCode(user.id, code, expirationMinutes))
    }

    override fun sendWelcomeEmail(user: UserModel) {
        publishAfterCommit(MailCommand.Welcome(user.id))
    }

    override fun sendPasswordResetEmail(
        user: UserModel,
        resetToken: String,
        expirationHours: Long,
    ) {
        publishAfterCommit(MailCommand.PasswordReset(user.id, resetToken, expirationHours))
    }

    override fun sendApplicationSubmittedEmail(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        publishAfterCommit(MailCommand.ApplicationSubmitted(pm.id, applicant.id, project.id, message))
    }

    override fun sendApplicationDecidedEmail(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    ) {
        publishAfterCommit(MailCommand.ApplicationDecided(applicant.id, project.id, accepted, reason))
    }

    override fun sendProjectInvitationEmail(
        invitee: UserModel,
        pm: UserModel,
        project: ProjectModel,
        message: String?,
    ) {
        publishAfterCommit(MailCommand.ProjectInvitation(invitee.id, pm.id, project.id, message))
    }

    override fun sendProjectInvitationResponseEmail(
        pm: UserModel,
        employer: UserModel,
        project: ProjectModel,
        accepted: Boolean,
    ) {
        publishAfterCommit(MailCommand.ProjectInvitationResponse(pm.id, employer.id, project.id, accepted))
    }

    // Mail must not reach the queue if the surrounding transaction rolls back
    private fun publishAfterCommit(command: MailCommand) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        publish(command)
                    }
                },
            )
        } else {
            publish(command)
        }
    }

    private fun publish(command: MailCommand) {
        rabbitTemplate.convertAndSend(properties.exchange, properties.mailRoutingKey, MailEnvelope(TenantContext.get(), command))
    }
}
