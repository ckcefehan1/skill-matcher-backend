package org.efehan.skillmatcherbackend.core.superadmin

import org.efehan.skillmatcherbackend.config.properties.SuperadminProperties
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.core.role.RoleService
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Bootstrap for the first SUPERADMIN, the only way in: the superadmin API requires
 * the role and no product path assigns it. Set app.superadmin.email and the account
 * is created (once) plus invited through the normal invitation flow. The row hangs
 * in the platform company seeded by v0.26 — users.company_id is NOT NULL, and the
 * SUPERADMIN role keeps that company out of the JWT anyway.
 */
@Component
class SuperadminBootstrapInitializer(
    private val properties: SuperadminProperties,
    private val companyRepository: CompanyRepository,
    private val userService: UserService,
    private val roleService: RoleService,
    private val invitationService: InvitationService,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(SuperadminBootstrapInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (properties.email.isBlank()) return
        TenantContext.runAsRoot {
            if (userService.existsByEmail(properties.email)) return@runAsRoot

            val role =
                roleService.findRole(RoleName.SUPERADMIN.name)
                    ?: error("Role ${RoleName.SUPERADMIN.name} is missing — v0.26 seeds it")
            val platformCompany =
                companyRepository.findById(PLATFORM_COMPANY_ID).orElseThrow {
                    IllegalStateException("Platform company is missing — v0.26 seeds it")
                }

            logger.info("Bootstrapping SUPERADMIN '{}'", properties.email)
            val superadmin =
                UserModel(
                    email = properties.email,
                    passwordHash = null,
                    firstName = null,
                    lastName = null,
                    role = role,
                ).apply { companyId = platformCompany.id }
            userService.save(superadmin)
            invitationService.createAndSendInvitation(superadmin)
        }
    }

    companion object {
        const val PLATFORM_COMPANY_ID = "a0000000-0000-4000-8000-000000000003"
    }
}
