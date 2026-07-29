package org.efehan.skillmatcherbackend.core.audit

import java.time.Instant

data class AuditLogResponse(
    val id: String,
    val action: String,
    val actorId: String?,
    val actorEmail: String?,
    val targetId: String?,
    val detail: String?,
    val createdDate: Instant,
)
