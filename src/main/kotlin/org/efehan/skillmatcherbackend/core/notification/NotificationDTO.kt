package org.efehan.skillmatcherbackend.core.notification

import java.time.Instant

data class NotificationResponse(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val referenceId: String,
    val readAt: Instant?,
    val createdDate: Instant,
)

data class UnreadCountResponse(
    val count: Long,
)
