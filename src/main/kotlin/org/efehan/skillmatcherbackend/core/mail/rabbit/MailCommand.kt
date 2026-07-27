package org.efehan.skillmatcherbackend.core.mail.rabbit

sealed interface MailCommand {
    data class Invitation(
        val userId: String,
        val invitationToken: String,
        val expirationHours: Long,
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
