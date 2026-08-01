package org.efehan.skillmatcherbackend.config.properties

import org.efehan.skillmatcherbackend.persistence.CompanySize
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.standalone")
data class StandaloneProperties(
    val enabled: Boolean = false,
    val companyName: String = "",
    val companyStreet: String = "",
    val companyZip: String = "",
    val companyCity: String = "",
    val companyCountry: String = "",
    val adminEmail: String = "",
    val industry: String? = null,
    val companySize: CompanySize? = null,
    val website: String? = null,
)
