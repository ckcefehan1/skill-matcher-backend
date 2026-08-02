package org.efehan.skillmatcherbackend.core.role

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class RoleService(
    private val roleRepo: RoleRepository,
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
}
