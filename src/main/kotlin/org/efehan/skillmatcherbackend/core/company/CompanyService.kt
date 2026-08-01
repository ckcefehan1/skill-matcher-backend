package org.efehan.skillmatcherbackend.core.company

import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CompanyService(
    private val companyRepository: CompanyRepository,
) {
    /**
     * Unknown company id counts as enabled: the JWT signature already proves the claim was
     * issued by us, and failing closed here would lock a whole tenant out on a cache/race hiccup.
     */
    @Cacheable(cacheNames = [CacheConfig.COMPANY_ENABLED], key = "#companyId")
    fun isEnabled(companyId: String): Boolean = companyRepository.findById(companyId).map { it.isEnabled }.orElse(true)
}
