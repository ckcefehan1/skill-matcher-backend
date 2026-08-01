package org.efehan.skillmatcherbackend.core.mail

import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel

interface EmailService {
    fun sendInvitationEmail(
        user: UserModel,
        invitationToken: String,
        expirationHours: Long,
    )

    fun sendRegistrationCodeEmail(
        user: UserModel,
        code: String,
        expirationMinutes: Long,
    )

    fun sendWelcomeEmail(user: UserModel)

    fun sendPasswordResetEmail(
        user: UserModel,
        resetToken: String,
        expirationHours: Long,
    )

    fun sendApplicationSubmittedEmail(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    )

    fun sendApplicationDecidedEmail(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    )

    fun sendProjectInvitationEmail(
        invitee: UserModel,
        pm: UserModel,
        project: ProjectModel,
        message: String?,
    )

    fun sendProjectInvitationResponseEmail(
        pm: UserModel,
        employer: UserModel,
        project: ProjectModel,
        accepted: Boolean,
    )
}
