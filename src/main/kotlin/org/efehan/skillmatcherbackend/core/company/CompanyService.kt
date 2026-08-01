package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.CompanyModel
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val invitationService: InvitationService,
    private val auditService: AuditService,
) {
    /**
     * Unknown company id counts as enabled: the JWT signature already proves the claim was
     * issued by us, and failing closed here would lock a whole tenant out on a cache/race hiccup.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = [CacheConfig.COMPANY_ENABLED], key = "#companyId")
    fun isEnabled(companyId: String): Boolean = companyRepository.findById(companyId).map { it.isEnabled }.orElse(true)

    @Transactional(readOnly = true)
    fun list(): List<CompanyModel> = companyRepository.findAll()

    fun create(request: CreateCompanyRequest): CompanyModel {
        if (companyRepository.existsByName(request.name)) {
            throw DuplicateEntryException(
                resource = "Company",
                field = "name",
                value = request.name,
                errorCode = GlobalErrorCode.DUPLICATE_ENTRY,
                status = HttpStatus.CONFLICT,
            )
        }
        if (userRepository.existsByEmail(request.adminEmail)) {
            throw DuplicateEntryException(
                resource = "User",
                field = "email",
                value = request.adminEmail,
                errorCode = GlobalErrorCode.USER_ALREADY_EXISTS,
                status = HttpStatus.CONFLICT,
            )
        }

        val company =
            companyRepository.save(
                CompanyModel(
                    name = request.name,
                    street = request.street,
                    zip = request.zip,
                    city = request.city,
                    country = request.country,
                    industry = request.industry,
                    companySize = request.companySize,
                    website = request.website,
                    isEnabled = true,
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

        // root context (SUPERADMIN has no tenant): companyId goes on the entity explicitly.
        // Hibernate only permits that because the resolver reports the root tenant.
        val admin =
            UserModel(
                email = request.adminEmail,
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
        auditService.record(
            if (enabled) AuditAction.COMPANY_ENABLED else AuditAction.COMPANY_DISABLED,
            targetId = company.id,
            detail = "name=${company.name}",
        )
    }
}
