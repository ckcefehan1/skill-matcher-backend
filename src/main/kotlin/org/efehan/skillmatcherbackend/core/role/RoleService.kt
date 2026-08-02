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
    fun findRole(roleName: String): RoleModel? = roleRepo.findByName(roleName.uppercase())

    fun getRole(roleName: String): RoleModel =
        findRole(roleName)
            ?: throw EntryNotFoundException(
                resource = "Role",
                field = "name",
                value = roleName,
                errorCode = GlobalErrorCode.ROLE_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )

    @Transactional(readOnly = true)
    fun listRoles(): List<RoleModel> = roleRepo.findAll(Sort.by("name"))

    fun getRoleById(roleId: String): RoleModel =
        roleRepo.findByIdOrNull(roleId)
            ?: throw EntryNotFoundException(
                resource = "Role",
                field = "id",
                value = roleId,
                errorCode = GlobalErrorCode.ROLE_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )

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
        // roles are global, so this must see every tenant — it does because the caller is a
        // SUPERADMIN and their requests run in the root context, where Hibernate skips the filter
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
}
