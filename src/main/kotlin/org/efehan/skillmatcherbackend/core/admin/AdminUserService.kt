package org.efehan.skillmatcherbackend.core.admin

import org.efehan.skillmatcherbackend.config.WebSocketSessionRegistry
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.core.role.RoleService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AdminUserService(
    private val userService: UserService,
    private val roleService: RoleService,
    private val invitationService: InvitationService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionRegistry: WebSocketSessionRegistry,
    private val auditService: AuditService,
) {
    fun createUser(
        email: String,
        roleName: String,
    ): UserModel {
        requireAssignableRole(roleName)
        if (userService.existsByEmail(email)) {
            throw DuplicateEntryException(
                resource = "User",
                field = "email",
                value = email,
                errorCode = GlobalErrorCode.USER_ALREADY_EXISTS,
                status = HttpStatus.CONFLICT,
            )
        }

        val role = roleService.getRole(roleName)

        val user =
            UserModel(
                email = email,
                passwordHash = null,
                firstName = null,
                lastName = null,
                role = role,
            )
        user.isEnabled = false

        val savedUser = userService.save(user)

        invitationService.createAndSendInvitation(savedUser)

        auditService.record(AuditAction.USER_CREATED, targetId = savedUser.id, detail = savedUser.email)

        return savedUser
    }

    fun updateUserStatus(
        userId: String,
        enabled: Boolean,
    ) {
        val user = userService.getUser(userId)
        user.isEnabled = enabled
        userService.save(user)

        if (!enabled) {
            refreshTokenRepository.revokeAllUserTokens(userId)
            sessionRegistry.disconnect(userId)
        }

        auditService.record(
            if (enabled) AuditAction.USER_ENABLED else AuditAction.USER_DISABLED,
            targetId = userId,
            detail = user.email,
        )
    }

    fun listUsers(pageable: Pageable): Page<UserModel> = userService.listUsers(pageable)

    fun updateUserRole(
        userId: String,
        roleName: String,
    ) {
        requireAssignableRole(roleName)
        val user = userService.getUser(userId)
        val role = roleService.getRole(roleName)

        val previousRole = user.role.name
        user.role = role
        userService.save(user)
        refreshTokenRepository.revokeAllUserTokens(user.id)

        auditService.record(
            AuditAction.USER_ROLE_CHANGED,
            targetId = user.id,
            detail = "$previousRole -> ${role.name}",
        )
    }

    /**
     * A company ADMIN must never mint a SUPERADMIN: that role drops the companyId
     * from the JWT and turns the next login into unfiltered root access.
     */
    private fun requireAssignableRole(roleName: String) {
        if (roleName.uppercase() !in ASSIGNABLE_ROLES) {
            throw AccessDeniedException(
                resource = "Role",
                errorCode = GlobalErrorCode.FORBIDDEN,
                status = HttpStatus.FORBIDDEN,
                message = "Role '$roleName' cannot be assigned by a company admin.",
            )
        }
    }

    private companion object {
        val ASSIGNABLE_ROLES =
            setOf(
                RoleName.ADMIN.name,
                RoleName.PROJECTMANAGER.name,
                RoleName.EMPLOYER.name,
            )
    }
}
