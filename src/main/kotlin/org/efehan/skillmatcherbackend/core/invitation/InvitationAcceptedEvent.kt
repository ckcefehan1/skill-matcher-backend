package org.efehan.skillmatcherbackend.core.invitation

import org.efehan.skillmatcherbackend.persistence.UserModel

/** Published synchronously inside the acceptance transaction. */
data class InvitationAcceptedEvent(
    val user: UserModel,
)
