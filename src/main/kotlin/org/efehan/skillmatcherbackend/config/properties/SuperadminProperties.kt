package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.superadmin")
data class SuperadminProperties(
    /** Bootstrap email for the first SUPERADMIN account; blank = no bootstrap. */
    val email: String = "",
)
