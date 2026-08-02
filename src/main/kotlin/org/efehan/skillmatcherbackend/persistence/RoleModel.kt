package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(name = "roles")
class RoleModel(
    @Column(name = "name", nullable = false, unique = true)
    var name: String,
    @Column(name = "description", nullable = true)
    var description: String?,
) : AuditingBaseEntity()

@Repository
interface RoleRepository : JpaRepository<RoleModel, String> {
    fun findByName(name: String): RoleModel?
}

enum class RoleName {
    PROJECTMANAGER,
    ADMIN,
    EMPLOYER,
    SUPERADMIN,
    ;

    companion object {
        fun isBuiltIn(name: String): Boolean = entries.any { it.name == name }
    }
}
