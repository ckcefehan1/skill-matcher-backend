package org.efehan.skillmatcherbackend.fixtures.builder

import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType

class SkillRelationBuilder {
    fun build(
        fromSkill: SkillModel = SkillBuilder().build(name = "kotlin"),
        toSkill: SkillModel = SkillBuilder().build(name = "java"),
        relationType: SkillRelationType = SkillRelationType.SIMILAR_TO,
        transferPenalty: Double = 0.7,
        source: SkillRelationSource = SkillRelationSource.CURATED,
    ): SkillRelationModel =
        SkillRelationModel(
            fromSkill = fromSkill,
            toSkill = toSkill,
            relationType = relationType,
            transferPenalty = transferPenalty,
            source = source,
        )
}
