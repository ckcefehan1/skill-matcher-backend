package org.efehan.skillmatcherbackend.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.efehan.skillmatcherbackend.core.chat.ChatUserResponse
import org.efehan.skillmatcherbackend.core.chat.ConversationResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity
@Table(name = "conversations")
class ConversationModel(
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val userOne: UserModel,
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_two_id", nullable = false)
    val userTwo: UserModel,
) : AuditingBaseEntity() {
    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null

    fun toDTO(
        currentUser: UserModel,
        lastMessage: ChatMessageModel?,
        unreadCount: Long = 0,
        partnerOnline: Boolean = false,
    ): ConversationResponse {
        val partner = if (userOne.id == currentUser.id) userTwo else userOne
        return ConversationResponse(
            id = id,
            partner =
                ChatUserResponse(
                    id = partner.id,
                    firstName = partner.firstName ?: "",
                    lastName = partner.lastName ?: "",
                    online = partnerOnline,
                ),
            lastMessage = lastMessage?.toDTO(),
            unreadCount = unreadCount,
            createdDate = createdDate!!,
        )
    }
}

@Repository
interface ConversationRepository : JpaRepository<ConversationModel, String> {
    @Query(
        """
          SELECT c FROM ConversationModel c
          WHERE c.userOne = :user OR c.userTwo = :user
          ORDER BY c.lastMessageAt DESC NULLS LAST
          """,
    )
    fun findByUser(user: UserModel): List<ConversationModel>

    @Query(
        """
          SELECT c FROM ConversationModel c
          WHERE (c.userOne = :userOne AND c.userTwo = :userTwo)
             OR (c.userOne = :userTwo AND c.userTwo = :userOne)
          """,
    )
    fun findByUsers(
        userOne: UserModel,
        userTwo: UserModel,
    ): ConversationModel?

    @Query(
        """
          SELECT CASE WHEN c.userOne = :user THEN c.userTwo.id ELSE c.userOne.id END
          FROM ConversationModel c
          WHERE c.userOne = :user OR c.userTwo = :user
          """,
    )
    fun findPartnerIds(user: UserModel): List<String>
}
