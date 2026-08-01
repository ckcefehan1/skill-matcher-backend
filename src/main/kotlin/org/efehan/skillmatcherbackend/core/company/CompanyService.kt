package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.config.WebSocketSessionRegistry
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.invitation.InvitationAcceptedEvent
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.CompanyModel
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// ponytail: COMPANY_ENABLED is process-local (60s TTL) — with more than one instance a
// disabled tenant keeps working on the other nodes until TTL expiry. Shared cache when
// multi-instance SaaS ships.
@Service
@Transactional
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val invitationService: InvitationService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionRegistry: WebSocketSessionRegistry,
    private val auditService: AuditService,
) {
    private val logger = LoggerFactory.getLogger(CompanyService::class.java)

    /**
     * Unknown company id counts as disabled: the zombie job may have deleted the company
     * while tokens still reference it, and a missing row must never open the gate.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = [CacheConfig.COMPANY_ENABLED], key = "#companyId")
    fun isEnabled(companyId: String): Boolean = companyRepository.findById(companyId).map { it.isEnabled }.orElse(false)

    @Transactional(readOnly = true)
    fun list(): List<CompanyModel> = companyRepository.findAll()

    /**
     * The one company-provisioning path, used by the superadmin API and public
     * self-registration. Runs in root context — companyId goes on the entities
     * explicitly because no session tenant exists. On the public path
     * ([ProvisionCompanyCommand.selfRegistered]) duplicates are answered like a
     * success (returned as null) so the endpoint leaks neither registered emails
     * nor taken company names.
     */
    fun provision(command: ProvisionCompanyCommand): CompanyModel? {
        if (companyRepository.existsByName(command.name)) {
            if (command.selfRegistered) {
                logger.info("Registration with already-taken company name suppressed")
                return null
            }
            throw DuplicateEntryException(
                resource = "Company",
                field = "name",
                value = command.name,
                errorCode = GlobalErrorCode.DUPLICATE_ENTRY,
                status = HttpStatus.CONFLICT,
            )
        }
        if (userRepository.existsByEmail(command.adminEmail)) {
            if (command.selfRegistered) {
                logger.info("Registration with already-known email suppressed")
                return null
            }
            throw DuplicateEntryException(
                resource = "User",
                field = "email",
                value = command.adminEmail,
                errorCode = GlobalErrorCode.USER_ALREADY_EXISTS,
                status = HttpStatus.CONFLICT,
            )
        }

        val company =
            companyRepository.save(
                CompanyModel(
                    name = command.name,
                    street = command.street,
                    zip = command.zip,
                    city = command.city,
                    country = command.country.uppercase(),
                    industry = command.industry,
                    companySize = command.companySize,
                    website = command.website,
                    isEnabled = !command.selfRegistered,
                    selfRegistered = command.selfRegistered,
                ),
            )

        val adminRole =
            roleRepository.findByName(RoleName.ADMIN.name)
                ?: throw EntryNotFoundException(
                    resource = "Role",
                    field = "name",
                    value = RoleName.ADMIN.name,
                    errorCode = GlobalErrorCode.ROLE_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )

        val admin =
            UserModel(
                email = command.adminEmail,
                passwordHash = null,
                firstName = null,
                lastName = null,
                role = adminRole,
            ).apply { companyId = company.id }
        userRepository.save(admin)

        invitationService.createAndSendInvitation(admin)
        auditService.record(
            AuditAction.COMPANY_REGISTERED,
            targetId = company.id,
            detail = "name=${company.name}",
            companyId = company.id,
        )

        return company
    }

    @CacheEvict(cacheNames = [CacheConfig.COMPANY_ENABLED], key = "#companyId")
    fun setEnabled(
        companyId: String,
        enabled: Boolean,
    ) {
        val company =
            companyRepository.findById(companyId).orElseThrow {
                EntryNotFoundException(
                    resource = "Company",
                    field = "id",
                    value = companyId,
                    errorCode = GlobalErrorCode.COMPANY_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }
        company.isEnabled = enabled
        companyRepository.save(company)

        if (!enabled) {
            // same semantics as disabling a single user: kill tokens and live sessions
            userRepository.findAllByCompanyId(companyId).forEach { user ->
                refreshTokenRepository.revokeAllUserTokens(user.id)
                sessionRegistry.disconnect(user.id)
            }
        }

        auditService.record(
            if (enabled) AuditAction.COMPANY_ENABLED else AuditAction.COMPANY_DISABLED,
            targetId = company.id,
            detail = "name=${company.name}",
        )
    }

    /**
     * Invite acceptance doubles as the email-ownership proof for self-registered
     * companies: this is the moment their is_enabled flips. Only self-registered
     * ones — a company disabled by the platform stays disabled. Runs inside the
     * acceptance transaction (synchronous event).
     */
    @EventListener
    @CacheEvict(cacheNames = [CacheConfig.COMPANY_ENABLED], key = "#event.user.companyId")
    fun on(event: InvitationAcceptedEvent) {
        val user = event.user
        val company = companyRepository.findById(user.companyId).orElse(null) ?: return
        if (!company.selfRegistered || company.isEnabled) return

        company.isEnabled = true
        companyRepository.save(company)
        auditService.record(
            AuditAction.COMPANY_ENABLED,
            actor = user,
            targetId = company.id,
            detail = "name=${company.name}, via invitation acceptance",
            companyId = company.id,
        )
    }
}
