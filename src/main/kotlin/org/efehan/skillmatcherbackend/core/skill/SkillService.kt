package org.efehan.skillmatcherbackend.core.skill

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class SkillService(
    private val skillRepo: SkillRepository,
) {
    @Cacheable(cacheNames = [CacheConfig.SKILL_CATALOG])
    fun getAllSkills(pageable: Pageable): Page<SkillModel> = skillRepo.findAll(pageable)

    /** Two requests can add the same unknown skill at once; the unique index on name decides the winner. */
    fun findOrCreate(rawName: String): SkillModel {
        val name = rawName.trim().lowercase()
        return skillRepo.findByNameIgnoreCase(name) ?: run {
            skillRepo.insertIfAbsent(UUID.randomUUID().toString(), name)
            checkNotNull(skillRepo.findByNameIgnoreCase(name))
        }
    }
}
