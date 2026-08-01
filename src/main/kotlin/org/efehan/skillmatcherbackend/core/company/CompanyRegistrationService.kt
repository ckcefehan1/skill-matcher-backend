package org.efehan.skillmatcherbackend.core.company

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
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CompanyRegistrationService(
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val invitationService: InvitationService,
    private val auditService: AuditService,
) {
    private val logger = LoggerFactory.getLogger(CompanyRegistrationService::class.java)

    /**
     * Runs in root context (public endpoint, no session tenant) — companyId goes on
     * the entities explicitly. An existing adminEmail is answered like a success so
     * the endpoint cannot be used to probe which emails are registered.
     */
    fun register(request: CreateCompanyRequest) {
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
            logger.info("Registration with already-known email suppressed")
            return
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
                    isEnabled = false,
                    selfRegistered = true,
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
    }
}
