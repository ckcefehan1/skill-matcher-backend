package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.properties.InvitationProperties
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
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
 * (a platform-disabled customer is not a zombie).
 */
@Component
class ZombieCompanyCleanupJob(
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val invitationTokenRepository: InvitationTokenRepository,
    private val invitationProperties: InvitationProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(ZombieCompanyCleanupJob::class.java)

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    fun cleanup() {
        val cutoff = Instant.now(clock).minus(invitationProperties.tokenExpirationHours, ChronoUnit.HOURS)
        val zombies =
            companyRepository
                .findBySelfRegisteredTrueAndIsEnabledFalseAndCreatedDateBefore(cutoff)
                .filter { company -> userRepository.findAllByCompanyId(company.id).none { it.isEnabled } }

        zombies.forEach { company ->
            logger.info("Deleting zombie company '{}' ({})", company.name, company.id)
            userRepository.findAllByCompanyId(company.id).forEach { user ->
                invitationTokenRepository.deleteByUser(user)
                userRepository.delete(user)
            }
            companyRepository.delete(company)
        }
    }
}
