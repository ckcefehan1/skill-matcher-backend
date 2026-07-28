package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.notification.NotificationResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

enum class NotificationType {
    CHAT_MESSAGE,
}

@Entity
@Table(name = "notifications")
class NotificationModel(
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserModel,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: NotificationType,
    @Column(name = "title", nullable = false)
    val title: String,
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    val body: String,
    @Column(name = "reference_id", nullable = false)
    val referenceId: String,
) : AuditingBaseEntity() {
    @Column(name = "read_at")
    var readAt: Instant? = null

    fun toDTO() =
        NotificationResponse(
            id = id,
            type = type.name,
            title = title,
            body = body,
            referenceId = referenceId,
            readAt = readAt,
            createdDate = createdDate!!,
        )
}

@Repository
interface NotificationRepository : JpaRepository<NotificationModel, String> {
    fun findByUserOrderByCreatedDateDesc(
        user: UserModel,
        pageable: Pageable,
    ): List<NotificationModel>

    fun countByUserAndReadAtIsNull(user: UserModel): Long

    fun existsByUserAndTypeAndReferenceId(
        user: UserModel,
        type: NotificationType,
        referenceId: String,
    ): Boolean

    @Modifying(clearAutomatically = true)
    @Query(
        """
          UPDATE NotificationModel n SET n.readAt = :readAt
          WHERE n.user = :user AND n.readAt IS NULL
          """,
    )
    fun markAllRead(
        user: UserModel,
        readAt: Instant,
    ): Int
}
