package org.efehan.skillmatcherbackend.core.notification

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.NotificationModel
import org.efehan.skillmatcherbackend.persistence.NotificationRepository
import org.efehan.skillmatcherbackend.persistence.NotificationType
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class NotificationService(
    private val notificationRepo: NotificationRepository,
) {
    fun list(
        user: UserModel,
        limit: Int,
    ): List<NotificationModel> = notificationRepo.findByUserOrderByCreatedDateDesc(user, PageRequest.of(0, limit.coerceIn(1, 100)))

    fun unreadCount(user: UserModel): Long = notificationRepo.countByUserAndReadAtIsNull(user)

    fun markRead(
        user: UserModel,
        notificationId: String,
    ) {
        val notification =
            notificationRepo.findById(notificationId).orElseThrow {
                EntryNotFoundException(
                    resource = "Notification",
                    field = "id",
                    value = notificationId,
                    errorCode = GlobalErrorCode.NOTIFICATION_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }
        if (notification.user.id != user.id) {
            throw EntryNotFoundException(
                resource = "Notification",
                field = "id",
                value = notificationId,
                errorCode = GlobalErrorCode.NOTIFICATION_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
            )
        }
        notification.readAt = Instant.now()
    }

    fun markAllRead(user: UserModel) {
        notificationRepo.markAllRead(user, Instant.now())
    }

    /**
     * referenceId = message id — dedupes redeliveries from at-least-once
     * RabbitMQ consumption via the (user, type, reference) unique constraint.
     *
     * A concurrent insert that slips past the pre-check is left to fail: the
     * constraint violation rolls this transaction back and the listener retries,
     * where the pre-check then wins. Catching it here cannot work — the id is
     * entity-assigned, so the INSERT is deferred to flush time.
     */
    fun createChatNotification(
        recipient: UserModel,
        senderName: String,
        messageId: String,
        messagePreview: String,
    ): NotificationModel? {
        if (notificationRepo.existsByUserAndTypeAndReferenceId(recipient, NotificationType.CHAT_MESSAGE, messageId)) {
            return null
        }
        return notificationRepo.save(
            NotificationModel(
                user = recipient,
                type = NotificationType.CHAT_MESSAGE,
                title = "Nachricht von $senderName",
                body = messagePreview.take(200),
                referenceId = messageId,
            ),
        )
    }
}
