package org.efehan.skillmatcherbackend.core.mail.rabbit

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * companyId rides along so the listener can scope its DB work to the tenant that
 * published the command. Null = root context (e.g. company self-registration).
 */
data class MailEnvelope(
    val companyId: String?,
    val command: MailCommand,
)

/**
 * Nested inside the envelope the AMQP `__TypeId__` header only names the envelope, so the
 * concrete command type has to travel in the payload itself.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = MailCommand.Invitation::class, name = "invitation"),
    JsonSubTypes.Type(value = MailCommand.RegistrationCode::class, name = "registration-code"),
    JsonSubTypes.Type(value = MailCommand.Welcome::class, name = "welcome"),
    JsonSubTypes.Type(value = MailCommand.PasswordReset::class, name = "password-reset"),
    JsonSubTypes.Type(value = MailCommand.ApplicationSubmitted::class, name = "application-submitted"),
    JsonSubTypes.Type(value = MailCommand.ApplicationDecided::class, name = "application-decided"),
    JsonSubTypes.Type(value = MailCommand.ProjectInvitation::class, name = "project-invitation"),
    JsonSubTypes.Type(value = MailCommand.ProjectInvitationResponse::class, name = "project-invitation-response"),
)
sealed interface MailCommand {
    data class Invitation(
        val userId: String,
        val invitationToken: String,
        val expirationHours: Long,
    ) : MailCommand

    data class RegistrationCode(
        val userId: String,
        val code: String,
        val expirationMinutes: Long,
    ) : MailCommand

    data class Welcome(
        val userId: String,
    ) : MailCommand

    data class PasswordReset(
        val userId: String,
        val resetToken: String,
        val expirationHours: Long,
    ) : MailCommand

    data class ApplicationSubmitted(
        val pmId: String,
        val applicantId: String,
        val projectId: String,
        val message: String?,
    ) : MailCommand

    data class ApplicationDecided(
        val applicantId: String,
        val projectId: String,
        val accepted: Boolean,
        val reason: String?,
    ) : MailCommand

    data class ProjectInvitation(
        val inviteeId: String,
        val pmId: String,
        val projectId: String,
        val message: String?,
    ) : MailCommand

    data class ProjectInvitationResponse(
        val pmId: String,
        val employerId: String,
        val projectId: String,
        val accepted: Boolean,
    ) : MailCommand
}
