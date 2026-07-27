package org.efehan.skillmatcherbackend.core.skill

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserSkillModel
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserSkillService(
    private val skillRepo: SkillRepository,
    private val userSkillRepo: UserSkillRepository,
) {
    @CacheEvict(
        cacheNames = [CacheConfig.SKILL_CATALOG, CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun addOrUpdateSkill(
        user: UserModel,
        name: String,
        level: Int,
    ): Pair<UserSkillModel, Boolean> {
        require(level in 1..5) { "Level must be between 1 and 5" }

        val normalized = name.trim().lowercase()
        val skill =
            skillRepo.findByNameIgnoreCase(normalized)
                ?: skillRepo.save(SkillModel(name = normalized))

        val existing = userSkillRepo.findByUserAndSkillId(user, skill.id)
        val created = existing == null

        val userSkill =
            if (existing != null) {
                existing.level = level
                userSkillRepo.save(existing)
            } else {
                userSkillRepo.save(UserSkillModel(user = user, skill = skill, level = level))
            }

        return userSkill to created
    }

    @Cacheable(cacheNames = [CacheConfig.SKILL_CATALOG])
    fun getAllSkills(pageable: Pageable): Page<SkillModel> = skillRepo.findAll(pageable)

    fun getUserSkills(user: UserModel): List<UserSkillModel> = userSkillRepo.findByUser(user)

    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun deleteSkill(
        user: UserModel,
        userSkillId: String,
    ) {
        val userSkill =
            userSkillRepo.findByIdOrNull(userSkillId)
                ?: throw EntryNotFoundException(
                    resource = "UserSkill",
                    field = "id",
                    value = userSkillId,
                    errorCode = GlobalErrorCode.USER_SKILL_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )

        if (userSkill.user.id != user.id) {
            throw AccessDeniedException(
                resource = "UserSkill",
                errorCode = GlobalErrorCode.USER_SKILL_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        userSkillRepo.delete(userSkill)
    }
}
