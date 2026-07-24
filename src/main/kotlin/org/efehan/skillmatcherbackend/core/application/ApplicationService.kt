package org.efehan.skillmatcherbackend.core.application

import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationModel
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
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
    private val projectRepo: ProjectRepository,
    private val memberRepo: ProjectMemberRepository,
    private val emailService: EmailService,
) {
    fun apply(
        user: UserModel,
        projectId: String,
        message: String?,
    ): ProjectApplicationModel {
        val project = findProjectOrThrow(projectId)

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

        if (applicationRepo.findByProjectAndUserAndStatus(project, user, ApplicationStatus.PENDING) != null) {
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
        return applicationRepo.save(application)
    }

    fun withdraw(
        user: UserModel,
        applicationId: String,
    ): ProjectApplicationModel {
        val application = findApplicationOrThrow(applicationId)
        if (application.user.id != user.id) {
            throw AccessDeniedException(
                resource = "ProjectApplication",
                errorCode = GlobalErrorCode.APPLICATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }
        ensurePending(application)

        application.status = ApplicationStatus.WITHDRAWN
        application.decidedAt = Instant.now()
        return applicationRepo.save(application)
    }

    @Transactional(readOnly = true)
    fun listForProject(
        pm: UserModel,
        projectId: String,
        pageable: Pageable,
    ): Page<ProjectApplicationModel> {
        val project = findProjectOrThrow(projectId)
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

    private fun findProjectOrThrow(projectId: String) =
        projectRepo.findByIdOrNull(projectId)
            ?: throw EntryNotFoundException(
                resource = "Project",
                field = "id",
                value = projectId,
                errorCode = GlobalErrorCode.PROJECT_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )

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
}
