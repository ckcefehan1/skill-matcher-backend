package org.efehan.skillmatcherbackend.core.skill

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.config.properties.SkillGraphProperties
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.persistence.SkillCoOccurrence
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationRepository
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class SkillRelationInfo(
    val relatedSkill: SkillModel,
    val transferPenalty: Double,
    val relationType: SkillRelationType,
    val source: SkillRelationSource,
)

@Service
class SkillGraphService(
    private val skillRelationRepo: SkillRelationRepository,
    private val userSkillRepo: UserSkillRepository,
    private val properties: SkillGraphProperties,
) {
    private val log = LoggerFactory.getLogger(SkillGraphService::class.java)

    /**
     * Returns, for each input skill, the list of related skills with their transfer penalty
     * (1.0 = identical fit, 0.0 = unrelated). Relations are bidirectional: a relation A→B also
     * makes B a relative of A. Relations below [SkillGraphProperties.minTransferPenalty] are filtered out.
     */
    @Transactional(readOnly = true)
    fun expandSkills(skills: List<SkillModel>): Map<String, List<SkillRelationInfo>> {
        if (!properties.enabled) return emptyMap()
        if (skills.isEmpty()) return emptyMap()
        val byId = skills.associateBy { it.id }
        val relations = skillRelationRepo.findBySkillIn(skills)

        val result = mutableMapOf<String, MutableList<SkillRelationInfo>>()
        for (relation in relations) {
            if (relation.transferPenalty < properties.minTransferPenalty) continue
            val (fromId, toSkill) =
                if (byId.containsKey(relation.fromSkill.id)) {
                    relation.fromSkill.id to relation.toSkill
                } else if (byId.containsKey(relation.toSkill.id)) {
                    relation.toSkill.id to relation.fromSkill
                } else {
                    continue
                }
            result
                .getOrPut(fromId) { mutableListOf() }
                .add(
                    SkillRelationInfo(
                        relatedSkill = toSkill,
                        transferPenalty = relation.transferPenalty,
                        relationType = relation.relationType,
                        source = relation.source,
                    ),
                )
        }
        return result
    }

    /**
     * Nightly job: derives LEARNED relations from user_skills co-occurrence.
     * For each pair of skills that co-occur in at least [SkillGraphProperties.minCoOccurrence] users,
     * inserts or updates a LEARNED relation with `transferPenalty = 0.3 + 0.4 * (count / maxCount)`.
     * Existing CURATED relations are preserved (LEARNED is skipped for pairs that already have CURATED).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @CacheEvict(
        cacheNames = [CacheConfig.MATCHING_CANDIDATES, CacheConfig.MATCHING_PROJECTS_FOR_USER],
        allEntries = true,
    )
    @Transactional
    fun deriveCoOccurrence() {
        if (!properties.derivationEnabled) return

        // the skill graph is global, so co-occurrence
        // deliberately aggregates user_skills across tenants — declared root context
        TenantContext.runAsRoot {
            val coOccurrences =
                userSkillRepo.findSkillCoOccurrence(properties.minCoOccurrence.toLong())
            if (coOccurrences.isEmpty()) return@runAsRoot

            val maxCount = coOccurrences.maxOf { it.count }.toDouble()
            val curatedPairs = loadCuratedPairs(coOccurrences)

            var inserted = 0
            var updated = 0
            for (co in coOccurrences) {
                val pairKey = pairKey(co.fromSkill.id, co.toSkill.id)
                if (pairKey in curatedPairs) continue

                val transferPenalty = computeLearnedPenalty(co.count, maxCount)
                val existing =
                    skillRelationRepo.findByFromSkillAndToSkill(co.fromSkill, co.toSkill)

                if (existing == null) {
                    skillRelationRepo.save(
                        SkillRelationModel(
                            fromSkill = co.fromSkill,
                            toSkill = co.toSkill,
                            relationType = SkillRelationType.SIMILAR_TO,
                            transferPenalty = transferPenalty,
                            source = SkillRelationSource.LEARNED,
                        ),
                    )
                    inserted++
                } else if (existing.source == SkillRelationSource.LEARNED) {
                    existing.transferPenalty = transferPenalty
                    updated++
                }
            }
            log.info("Co-occurrence derivation: inserted={}, updated={}, totalPairs={}", inserted, updated, coOccurrences.size)
        }
    }

    private fun loadCuratedPairs(coOccurrences: List<SkillCoOccurrence>): Set<String> {
        val skills = mutableSetOf<SkillModel>()
        for (co in coOccurrences) {
            skills.add(co.fromSkill)
            skills.add(co.toSkill)
        }
        return skillRelationRepo
            .findBySkillIn(skills)
            .asSequence()
            .filter { it.source == SkillRelationSource.CURATED }
            .map { pairKey(it.fromSkill.id, it.toSkill.id) }
            .toSet()
    }

    private fun pairKey(
        a: String,
        b: String,
    ): String = if (a < b) "$a|$b" else "$b|$a"

    private fun computeLearnedPenalty(
        count: Long,
        maxCount: Double,
    ): Double {
        if (maxCount <= 0.0) return 0.7
        val normalized = count.toDouble() / maxCount
        return roundToTwoDecimals(0.3 + 0.4 * normalized)
    }

    private fun roundToTwoDecimals(value: Double): Double = Math.round(value * 100.0) / 100.0
}
