package org.efehan.skillmatcherbackend.core.mail

import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class TemplateService(
    private val templateEngine: SpringTemplateEngine,
) {
    fun renderInvitation(
        firstName: String,
        invitationLink: String,
        expirationHours: Long,
    ): String {
        val context =
            Context().apply {
                setVariable("firstName", firstName)
                setVariable("invitationLink", invitationLink)
                setVariable("expirationHours", expirationHours)
            }
        return templateEngine.process("invitation", context)
    }

    fun renderRegistrationCode(
        code: String,
        expirationMinutes: Long,
    ): String {
        val context =
            Context().apply {
                setVariable("code", code)
                setVariable("expirationMinutes", expirationMinutes)
            }
        return templateEngine.process("registration-code", context)
    }

    fun renderWelcome(firstName: String): String {
        val context =
            Context().apply {
                setVariable("firstName", firstName)
            }
        return templateEngine.process("welcome", context)
    }

    fun renderPasswordReset(
        firstName: String,
        resetLink: String,
        expirationHours: Long,
    ): String {
        val context =
            Context().apply {
                setVariable("firstName", firstName)
                setVariable("resetLink", resetLink)
                setVariable("expirationHours", expirationHours)
            }
        return templateEngine.process("password-reset", context)
    }

    fun renderApplicationSubmitted(
        pm: UserModel,
        applicant: UserModel,
        project: ProjectModel,
        message: String?,
    ): String {
        val context =
            Context().apply {
                setVariable("pmFirstName", pm.firstName ?: "Project Manager")
                setVariable("applicantName", "${applicant.firstName ?: ""} ${applicant.lastName ?: ""}".trim())
                setVariable("applicantEmail", applicant.email)
                setVariable("projectName", project.name)
                setVariable("message", message)
            }
        return templateEngine.process("application-submitted", context)
    }

    fun renderApplicationDecided(
        applicant: UserModel,
        project: ProjectModel,
        accepted: Boolean,
        reason: String?,
    ): String {
        val context =
            Context().apply {
                setVariable("firstName", applicant.firstName ?: "User")
                setVariable("projectName", project.name)
                setVariable("accepted", accepted)
                setVariable("reason", reason)
            }
        return templateEngine.process("application-decided", context)
    }

    fun renderProjectInvitation(
        invitee: UserModel,
        pm: UserModel,
        project: ProjectModel,
        message: String?,
    ): String {
        val context =
            Context().apply {
                setVariable("firstName", invitee.firstName ?: "User")
                setVariable("pmName", "${pm.firstName ?: ""} ${pm.lastName ?: ""}".trim())
                setVariable("projectName", project.name)
                setVariable("message", message)
            }
        return templateEngine.process("project-invitation", context)
    }

    fun renderProjectInvitationResponse(
        pm: UserModel,
        employer: UserModel,
        project: ProjectModel,
        accepted: Boolean,
    ): String {
        val context =
            Context().apply {
                setVariable("pmFirstName", pm.firstName ?: "Project Manager")
                setVariable("employerName", "${employer.firstName ?: ""} ${employer.lastName ?: ""}".trim())
                setVariable("employerEmail", employer.email)
                setVariable("projectName", project.name)
                setVariable("accepted", accepted)
            }
        return templateEngine.process("project-invitation-response", context)
    }
}
