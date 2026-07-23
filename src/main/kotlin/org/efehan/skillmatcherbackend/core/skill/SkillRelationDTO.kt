package org.efehan.skillmatcherbackend.core.skill

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
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
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
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
