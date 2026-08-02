package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.efehan.skillmatcherbackend.core.role.RoleDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(
    name = "roles",
    uniqueConstraints = [UniqueConstraint(name = "uc_roles_company_name", columnNames = ["company_id", "name"])],
)
class RoleModel(
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "description", nullable = true)
    var description: String?,
) : TenantAwareEntity() {
    fun toDTO() =
        RoleDto(
            id = id,
            name = name,
            description = description,
            builtIn = RoleName.isBuiltIn(name),
        )
}

@Repository
interface RoleRepository : JpaRepository<RoleModel, String> {
    fun findByName(name: String): RoleModel?

    /** For root-context callers: without a session tenant [findByName] would match every company's copy. */
    fun findByCompanyIdAndName(
        companyId: String,
        name: String,
    ): RoleModel?
}

enum class RoleName(
    val defaultDescription: String,
) {
    PROJECTMANAGER("Project manager"),
    ADMIN("Company administrator"),
    EMPLOYER("Employer"),
    SUPERADMIN("Platform operator, sees all tenants"),
    ;

    companion object {
        fun isBuiltIn(name: String): Boolean = entries.any { it.name == name }
    }
}
