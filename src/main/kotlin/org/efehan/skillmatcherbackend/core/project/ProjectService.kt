package org.efehan.skillmatcherbackend.core.project

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class ProjectService(
    private val projectRepo: ProjectRepository,
    private val projectSkillRepo: ProjectSkillRepository,
    private val projectMemberRepo: ProjectMemberRepository,
) {
    fun createProject(
        owner: UserModel,
        name: String,
        description: String,
        startDate: LocalDate,
        endDate: LocalDate,
        maxMembers: Int,
    ): ProjectModel =
        projectRepo.save(
            ProjectModel(
                name = name,
                description = description,
                status = ProjectStatus.PLANNED,
                startDate = startDate,
                endDate = endDate,
                maxMembers = maxMembers,
                owner = owner,
            ),
        )

    fun getProject(projectId: String): ProjectModel =
        projectRepo.findByIdOrNull(projectId)
            ?: throw EntryNotFoundException(
                resource = "Project",
                field = "id",
                value = projectId,
                errorCode = GlobalErrorCode.PROJECT_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )

    fun getAllProjects(pageable: Pageable): Page<ProjectModel> = projectRepo.findAll(pageable)

    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun updateProject(
        user: UserModel,
        projectId: String,
        name: String,
        description: String,
        status: ProjectStatus,
        startDate: LocalDate,
        endDate: LocalDate,
        maxMembers: Int,
    ): ProjectModel {
        val project =
            projectRepo.findByIdOrNull(projectId)
                ?: throw EntryNotFoundException(
                    resource = "Project",
                    field = "id",
                    value = projectId,
                    errorCode = GlobalErrorCode.PROJECT_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
        if (project.owner.id != user.id) {
            throw AccessDeniedException(
                resource = "Project",
                errorCode = GlobalErrorCode.PROJECT_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        project.also {
            it.name = name
            it.description = description
            it.status = status
            it.startDate = startDate
            it.endDate = endDate
            it.maxMembers = maxMembers
        }

        return projectRepo.save(project)
    }

    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun deleteProject(
        user: UserModel,
        projectId: String,
    ) {
        val project =
            projectRepo.findByIdOrNull(projectId)
                ?: throw EntryNotFoundException(
                    resource = "Project",
                    field = "id",
                    value = projectId,
                    errorCode = GlobalErrorCode.PROJECT_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
        if (project.owner.id != user.id) {
            throw AccessDeniedException(
                resource = "Project",
                errorCode = GlobalErrorCode.PROJECT_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        projectMemberRepo.deleteAll(projectMemberRepo.findByProject(project))
        projectSkillRepo.deleteAll(projectSkillRepo.findByProject(project))
        projectRepo.delete(project)
    }
}
