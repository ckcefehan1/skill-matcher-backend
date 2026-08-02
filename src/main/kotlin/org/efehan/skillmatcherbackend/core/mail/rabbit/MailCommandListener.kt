package org.efehan.skillmatcherbackend.core.mail.rabbit

import org.efehan.skillmatcherbackend.core.mail.MailSender
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

// ponytail: no dedupe — a crash between SMTP send and ack can redeliver and send
// a duplicate mail. Add an idempotency key table if that ever hurts.
@Component
@ConditionalOnProperty(name = ["mail.rabbitmq.enabled"], havingValue = "true")
class MailCommandListener(
    private val mailSender: MailSender,
    private val userService: UserService,
    private val projectRepository: ProjectRepository,
) {
    private val logger = LoggerFactory.getLogger(MailCommandListener::class.java)

    @RabbitListener(queues = ["\${rabbitmq.mail-queue}"], containerFactory = "eventRabbitListenerContainerFactory")
    fun handle(envelope: MailEnvelope) {
        try {
            envelope.companyId?.let { TenantContext.set(it) } ?: TenantContext.allowRoot()
            handleCommand(envelope.command)
        } finally {
            TenantContext.clear()
        }
    }

    private fun handleCommand(command: MailCommand) {
        when (command) {
            is MailCommand.Invitation ->
                user(command.userId)?.let {
                    mailSender.sendInvitationEmail(it, command.invitationToken, command.expirationHours)
                } ?: drop("Invitation", command.userId)

            is MailCommand.RegistrationCode ->
                user(command.userId)?.let {
                    mailSender.sendRegistrationCodeEmail(it, command.code, command.expirationMinutes)
                } ?: drop("RegistrationCode", command.userId)

            is MailCommand.Welcome ->
                user(command.userId)?.let {
                    mailSender.sendWelcomeEmail(it)
                } ?: drop("Welcome", command.userId)

            is MailCommand.PasswordReset ->
                user(command.userId)?.let {
                    mailSender.sendPasswordResetEmail(it, command.resetToken, command.expirationHours)
                } ?: drop("PasswordReset", command.userId)

            is MailCommand.ApplicationSubmitted ->
                load(command.pmId, command.applicantId, command.projectId, "ApplicationSubmitted") { pm, applicant, project ->
                    mailSender.sendApplicationSubmittedEmail(pm, applicant!!, project, command.message)
                }

            is MailCommand.ApplicationDecided ->
                load(command.applicantId, null, command.projectId, "ApplicationDecided") { applicant, _, project ->
                    mailSender.sendApplicationDecidedEmail(applicant, project, command.accepted, command.reason)
                }

            is MailCommand.ProjectInvitation ->
                load(command.inviteeId, command.pmId, command.projectId, "ProjectInvitation") { invitee, pm, project ->
                    mailSender.sendProjectInvitationEmail(invitee, pm!!, project, command.message)
                }

            is MailCommand.ProjectInvitationResponse ->
                load(command.pmId, command.employerId, command.projectId, "ProjectInvitationResponse") { pm, employer, project ->
                    mailSender.sendProjectInvitationResponseEmail(pm, employer!!, project, command.accepted)
                }
        }
    }

    private fun load(
        firstUserId: String,
        secondUserId: String?,
        projectId: String,
        type: String,
        send: (UserModel, UserModel?, ProjectModel) -> Unit,
    ) {
        val first = user(firstUserId)
        val second = secondUserId?.let { user(it) }
        val project = projectRepository.findById(projectId).orElse(null)
        if (first == null || (secondUserId != null && second == null) || project == null) {
            logger.warn("Dropping mail command {}: referenced entity no longer exists", type)
            return
        }
        send(first, second, project)
    }

    private fun user(id: String): UserModel? = userService.findById(id)

    private fun drop(
        type: String,
        userId: String,
    ) {
        logger.warn("Dropping mail command {}: user {} no longer exists", type, userId)
    }
}
