package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.project.ProjectDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Entity
@Table(name = "projects")
class ProjectModel(
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "description", nullable = false)
    var description: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ProjectStatus,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "max_members", nullable = false)
    var maxMembers: Int,
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: UserModel,
) : TenantAwareEntity() {
    fun toDTO() =
        ProjectDto(
            id = id,
            name = name,
            description = description,
            status = status.name,
            startDate = startDate,
            endDate = endDate,
            maxMembers = maxMembers,
            ownerId = owner.id,
            ownerName = "${owner.firstName} ${owner.lastName}",
            createdDate = createdDate,
        )
}

@Repository
interface ProjectRepository : JpaRepository<ProjectModel, String> {
    fun findByStatusIn(statuses: Collection<ProjectStatus>): List<ProjectModel>

    @Query(
        """
        SELECT p
        FROM ProjectModel p
        WHERE p.status IN :statuses
          AND NOT EXISTS (
              SELECT pm.id
              FROM ProjectMemberModel pm
              WHERE pm.project = p
                AND pm.user = :user
                AND pm.status = :activeStatus
          )
        """,
    )
    fun findMatchableForUser(
        user: UserModel,
        statuses: Collection<ProjectStatus>,
        activeStatus: ProjectMemberStatus,
    ): List<ProjectModel>
}

enum class ProjectStatus {
    PLANNED,
    ACTIVE,
    PAUSED,
    COMPLETED,
}
