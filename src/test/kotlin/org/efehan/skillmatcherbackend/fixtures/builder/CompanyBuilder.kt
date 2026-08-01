package org.efehan.skillmatcherbackend.fixtures.builder

import org.efehan.skillmatcherbackend.persistence.CompanyModel

class CompanyBuilder {
    fun build(
        name: String = "Test GmbH",
        street: String = "Teststraße 1",
        zip: String = "12345",
        city: String = "Teststadt",
        country: String = "DE",
        industry: String? = null,
        website: String? = null,
        isEnabled: Boolean = true,
        selfRegistered: Boolean = false,
    ): CompanyModel =
        CompanyModel(
            name = name,
            street = street,
            zip = zip,
            city = city,
            country = country,
            industry = industry,
            website = website,
            isEnabled = isEnabled,
            selfRegistered = selfRegistered,
        )
}
