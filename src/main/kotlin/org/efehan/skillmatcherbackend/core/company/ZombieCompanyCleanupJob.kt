package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.properties.InvitationProperties
import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Removes self-registered companies whose invite was never accepted once the invite
 * validity has lapsed. Companies with at least one activated user are never touched
 * (a platform-disabled customer is not a zombie). Standalone mode has no
 * self-registration, so there is nothing to clean up. The advisory lock keeps
 * multi-instance deployments from deleting the same rows concurrently.
 */
@Component
class ZombieCompanyCleanupJob(
    private val companyRepository: CompanyRepository,
    private val userService: UserService,
    private val invitationTokenRepository: InvitationTokenRepository,
    private val invitationProperties: InvitationProperties,
    private val standaloneProperties: StandaloneProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(ZombieCompanyCleanupJob::class.java)

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    fun cleanup() {
        if (standaloneProperties.enabled) return
        TenantContext.runAsRoot {
            if (!companyRepository.tryAcquireZombieCleanupLock()) {
                logger.debug("Zombie cleanup skipped, another instance holds the lock")
                return@runAsRoot
            }

            val cutoff = Instant.now(clock).minus(invitationProperties.tokenExpirationHours, ChronoUnit.HOURS)
            val zombies =
                companyRepository
                    .findBySelfRegisteredTrueAndIsEnabledFalseAndCreatedDateBefore(cutoff)
                    .filter { company -> userService.listByCompany(company.id).none { it.isEnabled } }

            zombies.forEach { company ->
                logger.info("Deleting zombie company '{}' ({})", company.name, company.id)
                userService.listByCompany(company.id).forEach { user ->
                    invitationTokenRepository.deleteByUser(user)
                    userService.delete(user)
                }
                companyRepository.delete(company)
            }
        }
    }
}
