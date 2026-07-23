package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import java.security.Principal

/**
 * Principal for WebSocket sessions. Routes user destinations by user ID
 * instead of email, so message delivery does not depend on the email address.
 */
class WebSocketPrincipal(
    val securityUser: SecurityUser,
) : Principal {
    override fun getName(): String = securityUser.user.id
}
