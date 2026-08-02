package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.core.role.RoleService
import org.efehan.skillmatcherbackend.core.superadmin.SuperadminBootstrapInitializer.Companion.PLATFORM_COMPANY_ID
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.persistence.CompanyModel
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * On-prem bootstrap: one company, one admin, same binary as SaaS. Runs in root
 * context on purpose — tenant filtering would only get in the way here.
 */
@Component
@ConditionalOnProperty(name = ["app.standalone.enabled"], havingValue = "true")
class StandaloneDataInitializer(
    private val standaloneProperties: StandaloneProperties,
    private val companyRepository: CompanyRepository,
    private val userService: UserService,
    private val roleService: RoleService,
    private val invitationService: InvitationService,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(StandaloneDataInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        TenantContext.runAsRoot {
            val company = ensureCompany()
            ensureAdmin(company)
        }
    }

    private fun ensureCompany(): CompanyModel {
        // the Platform anchor is seeded by v0.26 in every install and is not the customer tenant
        companyRepository.findAll().firstOrNull { it.id != PLATFORM_COMPANY_ID }?.let { return it }

        val missing =
            mapOf(
                "company-name" to standaloneProperties.companyName,
                "company-street" to standaloneProperties.companyStreet,
                "company-zip" to standaloneProperties.companyZip,
                "company-city" to standaloneProperties.companyCity,
                "company-country" to standaloneProperties.companyCountry,
            ).filterValues { it.isBlank() }.keys
        check(missing.isEmpty()) {
            "app.standalone.enabled=true requires these properties: ${missing.joinToString(", ") { "app.standalone.$it" }}"
        }

        logger.info("Creating standalone company '{}'", standaloneProperties.companyName)
        return companyRepository.save(
            CompanyModel(
                name = standaloneProperties.companyName,
                street = standaloneProperties.companyStreet,
                zip = standaloneProperties.companyZip,
                city = standaloneProperties.companyCity,
                country = standaloneProperties.companyCountry,
                industry = standaloneProperties.industry,
                companySize = standaloneProperties.companySize,
                website = standaloneProperties.website,
                isEnabled = true,
            ),
        )
    }

    private fun ensureAdmin(company: CompanyModel) {
        check(standaloneProperties.adminEmail.isNotBlank()) {
            "app.standalone.enabled=true requires app.standalone.admin-email"
        }
        if (userService.existsByEmail(standaloneProperties.adminEmail)) return

        val adminRole =
            roleService.findRole(RoleName.ADMIN.name)
                ?: error("Role ${RoleName.ADMIN.name} is missing — cannot bootstrap standalone admin")

        logger.info("Creating standalone admin '{}'", standaloneProperties.adminEmail)
        val admin =
            UserModel(
                email = standaloneProperties.adminEmail,
                passwordHash = null,
                firstName = null,
                lastName = null,
                role = adminRole,
            ).apply { companyId = company.id }
        userService.save(admin)
        invitationService.createAndSendInvitation(admin)
    }
}
