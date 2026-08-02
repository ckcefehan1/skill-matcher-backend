package org.efehan.skillmatcherbackend.core.role

import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class RoleService(
    private val roleRepo: RoleRepository,
    private val userService: UserService,
) {
    /** Resolves inside the caller's tenant — Hibernate adds the `company_id` restriction. */
    fun findRole(roleName: String): RoleModel? = roleRepo.findByName(roleName.uppercase())

    /** For root-context callers, which have no ambient tenant to scope the lookup. */
    fun findRole(
        companyId: String,
        roleName: String,
    ): RoleModel? = roleRepo.findByCompanyIdAndName(companyId, roleName.uppercase())

    fun getRole(roleName: String): RoleModel = findRole(roleName) ?: throw roleNotFound("name", roleName)

    fun getRole(
        companyId: String,
        roleName: String,
    ): RoleModel = findRole(companyId, roleName) ?: throw roleNotFound("name", roleName)

    /**
     * Gives a company its own copy of the built-in catalog. Idempotent, so provisioning and the
     * standalone bootstrap can both call it on every start. SUPERADMIN is left out: it exists
     * only in the platform company, where the migration seeds it.
     */
    fun seedDefaults(companyId: String) {
        RoleName.entries
            .filter { it != RoleName.SUPERADMIN }
            .filter { roleRepo.findByCompanyIdAndName(companyId, it.name) == null }
            .forEach {
                roleRepo.save(
                    RoleModel(name = it.name, description = it.defaultDescription).apply { this.companyId = companyId },
                )
            }
    }

    @Transactional(readOnly = true)
    fun listRoles(): List<RoleModel> = roleRepo.findAll(Sort.by("name"))

    fun getRoleById(roleId: String): RoleModel = roleRepo.findByIdOrNull(roleId) ?: throw roleNotFound("id", roleId)

    /** Names are stored uppercase because every lookup and the `ROLE_` authority derive from them. */
    fun create(
        name: String,
        description: String?,
    ): RoleModel {
        val normalized = name.trim().uppercase()
        if (roleRepo.findByName(normalized) != null) {
            throw DuplicateEntryException(
                resource = "Role",
                field = "name",
                value = normalized,
                errorCode = GlobalErrorCode.ROLE_ALREADY_EXISTS,
                status = HttpStatus.CONFLICT,
            )
        }
        return roleRepo.save(RoleModel(name = normalized, description = description?.trim()))
    }

    /** Only the description is editable: a rename would silently invalidate every issued token's authority. */
    fun updateDescription(
        roleId: String,
        description: String?,
    ): RoleModel {
        val role = getRoleById(roleId)
        role.description = description?.trim()
        return roleRepo.save(role)
    }

    fun delete(roleId: String) {
        val role = getRoleById(roleId)
        if (RoleName.isBuiltIn(role.name)) {
            throw AccessDeniedException(
                resource = "Role",
                errorCode = GlobalErrorCode.ROLE_IMMUTABLE,
                status = HttpStatus.CONFLICT,
                message = "Built-in role '${role.name}' cannot be deleted.",
            )
        }
        // holders and roles live in the same tenant, so the ambient filter already covers this;
        // without the check the delete would fail on the FK as a 500
        if (userService.existsByRole(role)) {
            throw AccessDeniedException(
                resource = "Role",
                errorCode = GlobalErrorCode.ROLE_IN_USE,
                status = HttpStatus.CONFLICT,
                message = "Role '${role.name}' is still assigned to users.",
            )
        }
        roleRepo.delete(role)
    }

    private fun roleNotFound(
        field: String,
        value: String,
    ) = EntryNotFoundException(
        resource = "Role",
        field = field,
        value = value,
        errorCode = GlobalErrorCode.ROLE_NOT_FOUND,
        status = HttpStatus.NOT_FOUND,
    )
}
