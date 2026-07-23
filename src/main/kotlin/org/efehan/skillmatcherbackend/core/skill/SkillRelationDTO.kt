package org.efehan.skillmatcherbackend.core.skill

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.efehan.skillmatcherbackend.persistence.SkillRelationModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType

data class CreateSkillRelationRequest(
    @field:NotNull
    val fromSkillId: String,
    @field:NotNull
    val toSkillId: String,
    @field:NotNull
    val relationType: SkillRelationType,
    @field:Min(0)
    @field:Max(1)
    val transferPenalty: Double,
)

data class SkillRelationDto(
    val id: String,
    val fromSkillId: String,
    val fromSkillName: String,
    val toSkillId: String,
    val toSkillName: String,
    val relationType: SkillRelationType,
    val transferPenalty: Double,
    val source: SkillRelationSource,
)

fun SkillRelationModel.toDTO() =
    SkillRelationDto(
        id = id,
        fromSkillId = fromSkill.id,
        fromSkillName = fromSkill.name,
        toSkillId = toSkill.id,
        toSkillName = toSkill.name,
        relationType = relationType,
        transferPenalty = transferPenalty,
        source = source,
    )
