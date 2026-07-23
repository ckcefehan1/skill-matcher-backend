package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Entity
@Table(
    name = "skill_relations",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["from_skill_id", "to_skill_id", "relation_type"]),
    ],
    indexes = [
        Index(name = "idx_skill_relations_from", columnList = "from_skill_id"),
        Index(name = "idx_skill_relations_to", columnList = "to_skill_id"),
    ],
)
class SkillRelationModel(
    @ManyToOne(optional = false)
    @JoinColumn(name = "from_skill_id", nullable = false)
    val fromSkill: SkillModel,
    @ManyToOne(optional = false)
    @JoinColumn(name = "to_skill_id", nullable = false)
    val toSkill: SkillModel,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var relationType: SkillRelationType,
    @Column(nullable = false)
    var transferPenalty: Double,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: SkillRelationSource,
) : AuditingBaseEntity()

enum class SkillRelationType {
    SIMILAR_TO,
    PARENT_OF,
    PREREQUISITE_OF,
}

enum class SkillRelationSource {
    CURATED,
    LEARNED,
}

@Repository
interface SkillRelationRepository : JpaRepository<SkillRelationModel, String> {
    @Query(
        """
        SELECT sr FROM SkillRelationModel sr
        WHERE sr.fromSkill IN :skills OR sr.toSkill IN :skills
        """,
    )
    fun findBySkillIn(skills: Collection<SkillModel>): List<SkillRelationModel>

    fun findByFromSkillAndToSkill(
        fromSkill: SkillModel,
        toSkill: SkillModel,
    ): SkillRelationModel?

    fun existsByFromSkillAndToSkillAndRelationType(
        fromSkill: SkillModel,
        toSkill: SkillModel,
        relationType: SkillRelationType,
    ): Boolean
}
