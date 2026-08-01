package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * A STOMP session freezes its [org.efehan.skillmatcherbackend.core.auth.SecurityUser]
 * at CONNECT, so a role change or any deactivation path that misses
 * [WebSocketSessionRegistry.disconnect] would survive for the lifetime of the
 * connection. This re-checks the frozen snapshot against the database.
 *
 * A locked account is deliberately left alone: the lockout guards against login
 * brute force, and closing the victim's session would hand an attacker a DoS.
 */
@Component
class WebSocketSessionRevalidator(
    private val userRegistry: ObjectProvider<SimpUserRegistry>,
    private val sessionRegistry: WebSocketSessionRegistry,
    private val userRepository: UserRepository,
) {
    @Scheduled(fixedDelayString = "\${websocket.session-revalidation-interval}")
    fun revalidate() {
        // spans all tenants by design: connected users are not scoped to one company
        TenantContext.runAsRoot {
            val snapshots =
                userRegistry.getObject().users.mapNotNull { simpUser ->
                    (simpUser.principal as? WebSocketPrincipal)?.let { simpUser.name to it.securityUser.user.role.name }
                }
            if (snapshots.isEmpty()) return@runAsRoot

            val current = userRepository.findAllById(snapshots.map { it.first }).associateBy { it.id }

            snapshots.forEach { (userId, role) ->
                val user = current[userId]
                if (user == null || !user.isEnabled || user.role.name != role) {
                    sessionRegistry.disconnect(userId)
                }
            }
        }
    }
}
