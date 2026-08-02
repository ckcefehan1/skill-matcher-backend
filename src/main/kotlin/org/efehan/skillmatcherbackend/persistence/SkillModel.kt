package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.skill.SkillDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Entity
@Table(name = "skills")
class SkillModel(
    @Column(name = "name", nullable = false, unique = true)
    val name: String,
) : AuditingBaseEntity() {
    fun toDTO() =
        SkillDto(
            id = id,
            name = name,
        )
}

@Repository
interface SkillRepository : JpaRepository<SkillModel, String> {
    fun findByNameIgnoreCase(name: String): SkillModel?

    @Modifying
    @Transactional
    @Query(
        value = "INSERT INTO skills (id, name, created_date) VALUES (:id, :name, now()) ON CONFLICT (name) DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: String,
        @Param("name") name: String,
    )

    /** Two requests can add the same unknown skill at once; the unique index on name decides the winner. */
    fun findOrCreate(rawName: String): SkillModel {
        val name = rawName.trim().lowercase()
        return findByNameIgnoreCase(name) ?: run {
            insertIfAbsent(UUID.randomUUID().toString(), name)
            checkNotNull(findByNameIgnoreCase(name))
        }
    }
}
