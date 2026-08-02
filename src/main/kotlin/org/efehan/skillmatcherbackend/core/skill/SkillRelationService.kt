package org.efehan.skillmatcherbackend.core.skill

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.SkillRelationModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationRepository
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SkillRelationService(
    private val skillRelationRepo: SkillRelationRepository,
    private val skillService: SkillService,
) {
    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun create(
        fromSkillId: String,
        toSkillId: String,
        relationType: SkillRelationType,
        transferPenalty: Double,
    ): SkillRelationModel {
        require(fromSkillId != toSkillId) { "A skill cannot be related to itself" }

        val fromSkill = skillService.getSkill(fromSkillId)
        val toSkill = skillService.getSkill(toSkillId)

        if (skillRelationRepo.existsByFromSkillAndToSkillAndRelationType(fromSkill, toSkill, relationType)) {
            throw DuplicateEntryException(
                resource = "SkillRelation",
                field = "fromSkillId,toSkillId,relationType",
                value = "$fromSkillId,$toSkillId,${relationType.name}",
                errorCode = GlobalErrorCode.SKILL_RELATION_DUPLICATE,
                status = HttpStatus.CONFLICT,
            )
        }

        return skillRelationRepo.save(
            SkillRelationModel(
                fromSkill = fromSkill,
                toSkill = toSkill,
                relationType = relationType,
                transferPenalty = transferPenalty,
                source = SkillRelationSource.CURATED,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun listBySkill(skillId: String): List<SkillRelationModel> = skillRelationRepo.findBySkillIn(listOf(skillService.getSkill(skillId)))

    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    fun delete(id: String) {
        val relation =
            skillRelationRepo.findByIdOrNull(id)
                ?: throw EntryNotFoundException(
                    resource = "SkillRelation",
                    field = "id",
                    value = id,
                    errorCode = GlobalErrorCode.SKILL_RELATION_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
        skillRelationRepo.delete(relation)
    }
}
