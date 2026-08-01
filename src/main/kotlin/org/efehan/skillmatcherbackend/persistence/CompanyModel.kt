package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

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
) : AuditingBaseEntity()

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
}
