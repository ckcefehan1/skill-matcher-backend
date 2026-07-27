package org.efehan.skillmatcherbackend.core.skill

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.SkillRelationModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationRepository
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType
import org.efehan.skillmatcherbackend.persistence.SkillRepository
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
    private val skillRepo: SkillRepository,
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

        val fromSkill =
            skillRepo.findByIdOrNull(fromSkillId)
                ?: throw EntryNotFoundException(
                    resource = "Skill",
                    field = "id",
                    value = fromSkillId,
                    errorCode = GlobalErrorCode.SKILL_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
        val toSkill =
            skillRepo.findByIdOrNull(toSkillId)
                ?: throw EntryNotFoundException(
                    resource = "Skill",
                    field = "id",
                    value = toSkillId,
                    errorCode = GlobalErrorCode.SKILL_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )

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
    fun listBySkill(skillId: String): List<SkillRelationModel> {
        val skill =
            skillRepo.findByIdOrNull(skillId)
                ?: throw EntryNotFoundException(
                    resource = "Skill",
                    field = "id",
                    value = skillId,
                    errorCode = GlobalErrorCode.SKILL_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
        return skillRelationRepo.findBySkillIn(listOf(skill))
    }

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
