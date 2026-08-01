package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.company.CompanyResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity
@Table(name = "companies")
class CompanyModel(
    @Column(name = "name", nullable = false, unique = true)
    var name: String,
    @Column(name = "street", nullable = false)
    var street: String,
    @Column(name = "zip", nullable = false)
    var zip: String,
    @Column(name = "city", nullable = false)
    var city: String,
    @Column(name = "country", nullable = false, length = 2)
    var country: String,
    @Column(name = "industry")
    var industry: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "company_size", length = 50)
    var companySize: CompanySize? = null,
    @Column(name = "website")
    var website: String? = null,
    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = false,
    /** Self-registered companies start disabled and are activated by invite acceptance. */
    @Column(name = "self_registered", nullable = false)
    var selfRegistered: Boolean = false,
) : AuditingBaseEntity() {
    fun toDTO() =
        CompanyResponse(
            id = id,
            name = name,
            street = street,
            zip = zip,
            city = city,
            country = country,
            industry = industry,
            companySize = companySize?.name,
            website = website,
            enabled = isEnabled,
            createdDate = createdDate,
        )
}

enum class CompanySize {
    SIZE_1_10,
    SIZE_11_50,
    SIZE_51_200,
    SIZE_201_1000,
    SIZE_1000_PLUS,
}

@Repository
interface CompanyRepository : JpaRepository<CompanyModel, String> {
    fun existsByName(name: String): Boolean

    fun findBySelfRegisteredTrueAndIsEnabledFalseAndCreatedDateBefore(cutoff: Instant): List<CompanyModel>

    /** Transaction-scoped PG advisory lock so only one instance runs the zombie cleanup. */
    @Query(value = "SELECT pg_try_advisory_xact_lock(72727201)", nativeQuery = true)
    fun tryAcquireZombieCleanupLock(): Boolean
}
