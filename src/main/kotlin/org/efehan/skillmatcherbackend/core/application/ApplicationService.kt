package org.efehan.skillmatcherbackend.core.application

import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.core.project.ProjectService
import org.efehan.skillmatcherbackend.core.projectmember.ProjectMemberService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationModel
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class ApplicationService(
    private val applicationRepo: ProjectApplicationRepository,
    private val projectService: ProjectService,
    private val memberRepo: ProjectMemberRepository,
    private val userService: UserService,
    private val memberService: ProjectMemberService,
    private val emailService: EmailService,
    private val auditService: AuditService,
) {
    fun apply(
        user: UserModel,
        projectId: String,
        message: String?,
    ): ProjectApplicationModel {
        val project = projectService.getProject(projectId)

        val existingMember = memberRepo.findByProjectAndUser(project, user)
        if (existingMember != null && existingMember.status == ProjectMemberStatus.ACTIVE) {
            throw DuplicateEntryException(
                resource = "ProjectApplication",
                field = "userId",
                value = user.id,
                errorCode = GlobalErrorCode.APPLICATION_FOR_MEMBER,
                status = HttpStatus.CONFLICT,
                message = "User is already an active member of this project.",
            )
        }

        if (applicationRepo.findByProjectAndUserAndStatus(project, user, ApplicationStatus.PENDING) != null ||
            applicationRepo.findByProjectAndUserAndStatus(project, user, ApplicationStatus.INVITED) != null
        ) {
            throw DuplicateEntryException(
                resource = "ProjectApplication",
                field = "userId",
                value = user.id,
                errorCode = GlobalErrorCode.APPLICATION_DUPLICATE,
                status = HttpStatus.CONFLICT,
            )
        }

        val application =
            applicationRepo.save(
                ProjectApplicationModel(
                    project = project,
                    user = user,
                    status = ApplicationStatus.PENDING,
                    appliedAt = Instant.now(),
                    message = message,
                ),
            )
        emailService.sendApplicationSubmittedEmail(project.owner, user, project, message)
        return application
    }

    fun accept(
        pm: UserModel,
        applicationId: String,
    ): ProjectApplicationModel {
        val application = findApplicationOrThrow(applicationId)
        ensurePmOwnsProject(pm, application)
        ensurePending(application)

        application.status = ApplicationStatus.ACCEPTED
        application.decidedAt = Instant.now()
        application.decidedBy = pm

        emailService.sendApplicationDecidedEmail(
            applicant = application.user,
            project = application.project,
            accepted = true,
            reason = null,
        )
        recordDecision(AuditAction.APPLICATION_ACCEPTED, pm, application)
        return applicationRepo.save(application)
    }

    fun decline(
        pm: UserModel,
        applicationId: String,
        reason: String?,
    ): ProjectApplicationModel {
        val application = findApplicationOrThrow(applicationId)
        ensurePmOwnsProject(pm, application)
        ensurePending(application)

        application.status = ApplicationStatus.DECLINED
        application.decidedAt = Instant.now()
        application.decidedBy = pm
        if (reason != null) application.message = reason

        emailService.sendApplicationDecidedEmail(
            applicant = application.user,
            project = application.project,
            accepted = false,
            reason = reason,
        )
        recordDecision(AuditAction.APPLICATION_DECLINED, pm, application)
        return applicationRepo.save(application)
    }

    fun withdraw(
        user: UserModel,
        applicationId: String,
    ): ProjectApplicationModel {
        val application = findApplicationOrThrow(applicationId)
        ensureIsApplicant(user, application)
        ensurePending(application)

        application.status = ApplicationStatus.WITHDRAWN
        application.decidedAt = Instant.now()
        return applicationRepo.save(application)
    }

    fun invite(
        pm: UserModel,
        projectId: String,
        userId: String,
        message: String?,
    ): ProjectApplicationModel {
        val project = projectService.getProject(projectId)
        if (project.owner.id != pm.id) {
            throw AccessDeniedException(
                resource = "ProjectApplication",
                errorCode = GlobalErrorCode.APPLICATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }
        val user = userService.getUser(userId)

        val existingMember = memberRepo.findByProjectAndUser(project, user)
        if (existingMember != null && existingMember.status == ProjectMemberStatus.ACTIVE) {
            throw DuplicateEntryException(
                resource = "ProjectApplication",
                field = "userId",
                value = user.id,
                errorCode = GlobalErrorCode.APPLICATION_FOR_MEMBER,
                status = HttpStatus.CONFLICT,
                message = "User is already an active member of this project.",
            )
        }
        if (applicationRepo.findByProjectAndUserAndStatus(project, user, ApplicationStatus.PENDING) != null ||
            applicationRepo.findByProjectAndUserAndStatus(project, user, ApplicationStatus.INVITED) != null
        ) {
            throw DuplicateEntryException(
                resource = "ProjectApplication",
                field = "userId",
                value = user.id,
                errorCode = GlobalErrorCode.APPLICATION_DUPLICATE,
                status = HttpStatus.CONFLICT,
            )
        }

        val invitation =
            applicationRepo.save(
                ProjectApplicationModel(
                    project = project,
                    user = user,
                    status = ApplicationStatus.INVITED,
                    appliedAt = Instant.now(),
                    message = message,
                ),
            )
        emailService.sendProjectInvitationEmail(user, pm, project, message)
        return invitation
    }

    fun acceptInvitation(
        user: UserModel,
        applicationId: String,
    ): ProjectApplicationModel {
        val application = findApplicationOrThrow(applicationId)
        ensureIsApplicant(user, application)
        ensureInvited(application)

        application.status = ApplicationStatus.ACCEPTED
        application.decidedAt = Instant.now()
        application.decidedBy = user

        memberService.addMember(application.project.owner, application.project.id, user.id)

        emailService.sendProjectInvitationResponseEmail(
            pm = application.project.owner,
            employer = user,
            project = application.project,
            accepted = true,
        )
        recordDecision(AuditAction.APPLICATION_ACCEPTED, user, application)
        return applicationRepo.save(application)
    }

    fun declineInvitation(
        user: UserModel,
        applicationId: String,
    ): ProjectApplicationModel {
        val application = findApplicationOrThrow(applicationId)
        ensureIsApplicant(user, application)
        ensureInvited(application)

        application.status = ApplicationStatus.DECLINED
        application.decidedAt = Instant.now()
        application.decidedBy = user

        emailService.sendProjectInvitationResponseEmail(
            pm = application.project.owner,
            employer = user,
            project = application.project,
            accepted = false,
        )
        recordDecision(AuditAction.APPLICATION_DECLINED, user, application)
        return applicationRepo.save(application)
    }

    private fun recordDecision(
        action: AuditAction,
        actor: UserModel,
        application: ProjectApplicationModel,
    ) = auditService.record(
        action = action,
        actor = actor,
        targetId = application.id,
        detail = "project=${application.project.name} applicant=${application.user.email}",
    )

    @Transactional(readOnly = true)
    fun listForProject(
        pm: UserModel,
        projectId: String,
        pageable: Pageable,
    ): Page<ProjectApplicationModel> {
        val project = projectService.getProject(projectId)
        if (project.owner.id != pm.id) {
            throw AccessDeniedException(
                resource = "ProjectApplication",
                errorCode = GlobalErrorCode.APPLICATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }
        return if (pageable.sort.isUnsorted) {
            applicationRepo.findByProjectPendingFirst(project, pageable)
        } else {
            applicationRepo.findByProject(project, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun listForUser(
        user: UserModel,
        pageable: Pageable,
    ): Page<ProjectApplicationModel> = applicationRepo.findByUser(user, pageable)

    private fun findApplicationOrThrow(applicationId: String) =
        applicationRepo.findByIdOrNull(applicationId)
            ?: throw EntryNotFoundException(
                resource = "ProjectApplication",
                field = "id",
                value = applicationId,
                errorCode = GlobalErrorCode.APPLICATION_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )

    private fun ensurePmOwnsProject(
        pm: UserModel,
        application: ProjectApplicationModel,
    ) {
        if (application.project.owner.id != pm.id) {
            throw AccessDeniedException(
                resource = "ProjectApplication",
                errorCode = GlobalErrorCode.APPLICATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }
    }

    private fun ensurePending(application: ProjectApplicationModel) {
        if (application.status != ApplicationStatus.PENDING) {
            throw DuplicateEntryException(
                resource = "ProjectApplication",
                field = "status",
                value = application.status.name,
                errorCode = GlobalErrorCode.APPLICATION_ALREADY_DECIDED,
                status = HttpStatus.CONFLICT,
                message = "Application has already been decided (status=${application.status.name}).",
            )
        }
    }

    private fun ensureInvited(application: ProjectApplicationModel) {
        if (application.status != ApplicationStatus.INVITED) {
            throw DuplicateEntryException(
                resource = "ProjectApplication",
                field = "status",
                value = application.status.name,
                errorCode = GlobalErrorCode.APPLICATION_ALREADY_DECIDED,
                status = HttpStatus.CONFLICT,
                message = "Invitation has already been decided (status=${application.status.name}).",
            )
        }
    }

    private fun ensureIsApplicant(
        user: UserModel,
        application: ProjectApplicationModel,
    ) {
        if (application.user.id != user.id) {
            throw AccessDeniedException(
                resource = "ProjectApplication",
                errorCode = GlobalErrorCode.APPLICATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }
    }
}
