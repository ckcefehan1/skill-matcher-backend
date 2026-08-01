package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.persistence.CompanyModel
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
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
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val invitationService: InvitationService,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(StandaloneDataInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        ensureRoles()
        val company = ensureCompany()
        ensureAdmin(company)
    }

    private fun ensureRoles() {
        // a fresh on-prem install has no roles at all — they were never seeded by a migration
        listOf(RoleName.ADMIN, RoleName.PROJECTMANAGER, RoleName.EMPLOYER).forEach { roleName ->
            if (roleRepository.findByName(roleName.name) == null) {
                roleRepository.save(RoleModel(name = roleName.name, description = null))
            }
        }
    }

    private fun ensureCompany(): CompanyModel {
        companyRepository.findAll().firstOrNull()?.let { return it }

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
        if (userRepository.existsByEmail(standaloneProperties.adminEmail)) return

        val adminRole =
            roleRepository.findByName(RoleName.ADMIN.name)
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
        userRepository.save(admin)
        invitationService.createAndSendInvitation(admin)
    }
}
