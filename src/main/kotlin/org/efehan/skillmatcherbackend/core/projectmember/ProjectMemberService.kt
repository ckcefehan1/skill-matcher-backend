package org.efehan.skillmatcherbackend.core.projectmember

import org.efehan.skillmatcherbackend.core.project.ProjectService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ProjectMemberModel
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class ProjectMemberService(
    private val projectService: ProjectService,
    private val userService: UserService,
    private val memberRepo: ProjectMemberRepository,
) {
    @Transactional(readOnly = true)
    fun isActiveMember(
        project: ProjectModel,
        user: UserModel,
    ): Boolean = memberRepo.findByProjectAndUser(project, user)?.status == ProjectMemberStatus.ACTIVE

    fun addMember(
        owner: UserModel,
        projectId: String,
        userId: String,
    ): ProjectMemberModel {
        val project = projectService.getProjectAsOwner(owner, projectId)

        val user = userService.getUser(userId)

        // bereits aktives Mitglied?
        memberRepo.findByProjectAndUser(project, user)?.let { existing ->
            if (existing.status == ProjectMemberStatus.ACTIVE) {
                throw DuplicateEntryException(
                    resource = "ProjectMember",
                    field = "userId",
                    value = user.id,
                    errorCode = GlobalErrorCode.PROJECT_MEMBER_DUPLICATE,
                    status = HttpStatus.CONFLICT,
                )
            }
            // LEFT → reaktivieren
            existing.status = ProjectMemberStatus.ACTIVE
            return memberRepo.save(existing)
        }

        // maxMembers prüfen
        val activeCount = memberRepo.countByProjectAndStatus(project, ProjectMemberStatus.ACTIVE)
        if (activeCount >= project.maxMembers) {
            throw DuplicateEntryException(
                resource = "ProjectMember",
                field = "projectId",
                value = projectId,
                errorCode = GlobalErrorCode.PROJECT_FULL,
                status = HttpStatus.CONFLICT,
            )
        }

        return memberRepo.save(
            ProjectMemberModel(
                project = project,
                user = user,
                status = ProjectMemberStatus.ACTIVE,
                joinedDate = Instant.now(),
            ),
        )
    }

    fun getMembers(projectId: String): List<ProjectMemberModel> =
        memberRepo
            .findByProject(projectService.getProject(projectId))
            .filter { it.status == ProjectMemberStatus.ACTIVE }

    fun removeMember(
        owner: UserModel,
        projectId: String,
        userId: String,
    ) {
        val project = projectService.getProjectAsOwner(owner, projectId)

        val user = userService.getUser(userId)
        val member =
            memberRepo.findByProjectAndUser(project, user)
                ?: throw EntryNotFoundException(
                    resource = "ProjectMember",
                    field = "userId",
                    value = userId,
                    errorCode = GlobalErrorCode.PROJECT_MEMBER_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )

        member.status = ProjectMemberStatus.LEFT
        memberRepo.save(member)
    }

    fun leaveProject(
        user: UserModel,
        projectId: String,
    ) {
        val project = projectService.getProject(projectId)
        val member =
            memberRepo.findByProjectAndUser(project, user)
                ?: throw EntryNotFoundException(
                    resource = "ProjectMember",
                    field = "userId",
                    value = user.id,
                    errorCode = GlobalErrorCode.PROJECT_MEMBER_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )

        member.status = ProjectMemberStatus.LEFT
        memberRepo.save(member)
    }
}
