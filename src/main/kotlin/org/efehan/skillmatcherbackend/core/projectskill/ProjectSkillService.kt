package org.efehan.skillmatcherbackend.core.projectskill

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.core.project.ProjectService
import org.efehan.skillmatcherbackend.core.skill.SkillService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ProjectSkillModel
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProjectSkillService(
    private val projectService: ProjectService,
    private val skillService: SkillService,
    private val projectSkillRepo: ProjectSkillRepository,
) {
    @CacheEvict(
        cacheNames = [CacheConfig.SKILL_CATALOG, CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun addOrUpdateSkill(
        user: UserModel,
        projectId: String,
        name: String,
        level: Int,
        priorityName: String = SkillPriority.MUST_HAVE.name,
    ): Pair<ProjectSkillModel, Boolean> {
        require(level in 1..5) { "Level must be between 1 and 5" }
        val priority =
            SkillPriority.entries.firstOrNull {
                it.name.equals(priorityName.trim(), ignoreCase = true)
            } ?: throw IllegalArgumentException("Priority must be MUST_HAVE or NICE_TO_HAVE")

        val project = projectService.getProjectAsOwner(user, projectId)
        val skill = skillService.findOrCreate(name)

        val existing = projectSkillRepo.findByProjectAndSkillId(project, skill.id)

        val created = existing == null
        val projectSkill =
            if (existing != null) {
                existing.level = level
                existing.priority = priority
                projectSkillRepo.save(existing)
            } else {
                projectSkillRepo.save(
                    ProjectSkillModel(
                        project = project,
                        skill = skill,
                        level = level,
                        priority = priority,
                    ),
                )
            }

        return projectSkill to created
    }

    fun getProjectSkills(
        user: UserModel,
        projectId: String,
    ): List<ProjectSkillModel> = projectSkillRepo.findByProject(projectService.getProjectAsOwner(user, projectId))

    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun deleteSkill(
        user: UserModel,
        projectId: String,
        projectSkillId: String,
    ) {
        val project = projectService.getProjectAsOwner(user, projectId)

        val projectSkill =
            projectSkillRepo.findByIdOrNull(projectSkillId)
                ?: throw EntryNotFoundException(
                    resource = "ProjectSkill",
                    field = "id",
                    value = projectSkillId,
                    errorCode = GlobalErrorCode.PROJECT_SKILL_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )

        if (projectSkill.project.id != project.id) {
            throw AccessDeniedException(
                resource = "ProjectSkill",
                errorCode = GlobalErrorCode.PROJECT_SKILL_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        projectSkillRepo.delete(projectSkill)
    }
}
