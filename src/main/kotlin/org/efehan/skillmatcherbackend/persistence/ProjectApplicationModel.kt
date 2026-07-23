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
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity
@Table(
    name = "project_applications",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["project_id", "user_id", "status"]),
    ],
    indexes = [
        Index(name = "idx_applications_project_status", columnList = "project_id,status"),
        Index(name = "idx_applications_user", columnList = "user_id"),
    ],
)
class ProjectApplicationModel(
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    val project: ProjectModel,
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserModel,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ApplicationStatus,
    @Column(name = "applied_at", nullable = false)
    val appliedAt: Instant,
    @Column(name = "decided_at")
    var decidedAt: Instant? = null,
    @ManyToOne
    @JoinColumn(name = "decided_by_id")
    var decidedBy: UserModel? = null,
    @Column(name = "message", length = 1000)
    var message: String? = null,
) : AuditingBaseEntity()

enum class ApplicationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
}

@Repository
interface ProjectApplicationRepository : JpaRepository<ProjectApplicationModel, String> {
    fun findByProjectAndUserAndStatus(
        project: ProjectModel,
        user: UserModel,
        status: ApplicationStatus,
    ): ProjectApplicationModel?

    @Query(
        """
        SELECT a FROM ProjectApplicationModel a
        WHERE a.project = :project AND a.user IN :users AND a.status = :status
        """,
    )
    fun findByProjectAndUserInAndStatus(
        project: ProjectModel,
        users: Collection<UserModel>,
        status: ApplicationStatus,
    ): List<ProjectApplicationModel>

    @Query(
        """
        SELECT a FROM ProjectApplicationModel a
        WHERE a.user = :user AND a.project IN :projects
        ORDER BY a.appliedAt DESC
        """,
    )
    fun findByUserAndProjectIn(
        user: UserModel,
        projects: Collection<ProjectModel>,
    ): List<ProjectApplicationModel>

    @Query(
        value = """
        SELECT a FROM ProjectApplicationModel a
        WHERE a.project = :project
        """,
        countQuery = """
        SELECT count(a) FROM ProjectApplicationModel a
        WHERE a.project = :project
        """,
    )
    fun findByProject(
        project: ProjectModel,
        pageable: Pageable,
    ): Page<ProjectApplicationModel>

    fun findByUser(
        user: UserModel,
        pageable: Pageable,
    ): Page<ProjectApplicationModel>
}
