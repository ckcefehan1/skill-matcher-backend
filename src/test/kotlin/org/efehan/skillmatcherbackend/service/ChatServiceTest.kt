package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.chat.ChatService
import org.efehan.skillmatcherbackend.core.chat.ReadReceiptResponse
import org.efehan.skillmatcherbackend.core.chat.TypingResponse
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.ChatMessageBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ConversationBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ChatMessageRepository
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.Instant
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("ChatService Unit Tests")
class ChatServiceTest {
    @MockK
    private lateinit var conversationRepo: ConversationRepository

    @MockK
    private lateinit var messageRepo: ChatMessageRepository

    @MockK
    private lateinit var userRepo: UserRepository

    @MockK
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @InjectMockKs
    private lateinit var chatService: ChatService

    @Test
    fun `getConversations returns conversations for user`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findByUser(userA) } returns listOf(conversation)

        // when
        val result = chatService.getConversations(userA)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(conversation)
    }

    @Test
    fun `getConversations returns empty list when no conversations exist`() {
        // given
        val user = UserBuilder().build()
        every { conversationRepo.findByUser(user) } returns emptyList()

        // when
        val result = chatService.getConversations(user)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getLastMessages returns map keyed by conversation id`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        val message = ChatMessageBuilder().build(conversation = conversation, sender = userA)
        every { messageRepo.findLastMessagesByConversations(listOf(conversation)) } returns listOf(message)

        // when
        val result = chatService.getLastMessages(listOf(conversation))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[conversation.id]).isEqualTo(message)
    }

    @Test
    fun `getLastMessages returns empty map for empty input`() {
        // when
        val result = chatService.getLastMessages(emptyList())

        // then
        assertThat(result).isEmpty()
        verify(exactly = 0) { messageRepo.findLastMessagesByConversations(any()) }
    }

    @Test
    fun `getLastMessage returns message when exists`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        val message = ChatMessageBuilder().build(conversation = conversation, sender = userA)
        every { messageRepo.findTopByConversationOrderBySentAtDesc(conversation) } returns message

        // when
        val result = chatService.getLastMessage(conversation)

        // then
        assertThat(result).isEqualTo(message)
    }

    @Test
    fun `getLastMessage returns null when no messages exist`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { messageRepo.findTopByConversationOrderBySentAtDesc(conversation) } returns null

        // when
        val result = chatService.getLastMessage(conversation)

        // then
        assertThat(result).isNull()
    }

    @Test
    fun `getMessages returns messages for conversation member`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        val before = Instant.now()
        val message = ChatMessageBuilder().build(conversation = conversation, sender = userB, content = "Hi")
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messageRepo.findByConversationBefore(conversation, before, PageRequest.of(0, 20)) } returns listOf(message)

        // when
        val result = chatService.getMessages(userA, conversation.id, before, 20)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].content).isEqualTo("Hi")
    }

    @Test
    fun `getMessages throws EntryNotFoundException when conversation not found`() {
        // given
        every { conversationRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            chatService.getMessages(UserBuilder().build(), "nonexistent", Instant.now(), 20)
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_NOT_FOUND)
            })
    }

    @Test
    fun `getMessages throws AccessDeniedException when user is not a member`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)

        // then
        assertThatThrownBy {
            chatService.getMessages(otherUser, conversation.id, Instant.now(), 20)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_ACCESS_DENIED)
            })
    }

    @Test
    fun `createConversation creates new conversation when none exists`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        every { userRepo.findById(userB.id) } returns Optional.of(userB)
        every { conversationRepo.findByUsers(userA, userB) } returns null
        every { conversationRepo.save(any()) } returnsArgument 0

        // when
        val (conversation, created) = chatService.createConversation(userA, userB.id)

        // then
        assertThat(created).isTrue()
        verify(exactly = 1) { conversationRepo.save(any()) }
    }

    @Test
    fun `createConversation returns existing conversation when already exists`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val existing = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { userRepo.findById(userB.id) } returns Optional.of(userB)
        every { conversationRepo.findByUsers(userA, userB) } returns existing

        // when
        val (conversation, created) = chatService.createConversation(userA, userB.id)

        // then
        assertThat(created).isFalse()
        assertThat(conversation).isEqualTo(existing)
        verify(exactly = 0) { conversationRepo.save(any()) }
    }

    @Test
    fun `createConversation orders users by id`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        every { userRepo.findById(userB.id) } returns Optional.of(userB)
        every { conversationRepo.findByUsers(userA, userB) } returns null
        val slot = slot<ConversationModel>()
        every { conversationRepo.save(capture(slot)) } returnsArgument 0

        // when
        chatService.createConversation(userA, userB.id)

        // then
        val saved = slot.captured
        val expectedFirst = if (userA.id < userB.id) userA else userB
        val expectedSecond = if (userA.id < userB.id) userB else userA
        assertThat(saved.userOne).isEqualTo(expectedFirst)
        assertThat(saved.userTwo).isEqualTo(expectedSecond)
    }

    @Test
    fun `createConversation throws IllegalArgumentException when chatting with yourself`() {
        // given
        val user = UserBuilder().build()

        // then
        assertThatThrownBy {
            chatService.createConversation(user, user.id)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Cannot create a conversation with yourself.")

        verify(exactly = 0) { conversationRepo.save(any()) }
    }

    @Test
    fun `createConversation throws EntryNotFoundException when partner not found`() {
        // given
        every { userRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            chatService.createConversation(UserBuilder().build(), "nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.USER_NOT_FOUND)
            })

        verify(exactly = 0) { conversationRepo.save(any()) }
    }

    @Test
    fun `sendMessage saves message and sends to both users via websocket`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messageRepo.save(any()) } returnsArgument 0
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit

        // when
        val result = chatService.sendMessage(userA, conversation.id, "Hello Bob!")

        // then
        assertThat(result.content).isEqualTo("Hello Bob!")
        assertThat(result.sender).isEqualTo(userA)
        assertThat(result.conversation).isEqualTo(conversation)
        assertThat(result.sentAt).isNotNull()
        verify(exactly = 1) { messageRepo.save(any()) }
        verify(exactly = 1) { messagingTemplate.convertAndSendToUser(userB.id, "/queue/messages", any()) }
        verify(exactly = 1) { messagingTemplate.convertAndSendToUser(userA.id, "/queue/messages", any()) }
    }

    @Test
    fun `sendMessage throws EntryNotFoundException when conversation not found`() {
        // given
        every { conversationRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            chatService.sendMessage(UserBuilder().build(), "nonexistent", "Hello")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_NOT_FOUND)
            })

        verify(exactly = 0) { messageRepo.save(any()) }
    }

    @Test
    fun `sendMessage throws AccessDeniedException when user is not a member`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)

        // then
        assertThatThrownBy {
            chatService.sendMessage(otherUser, conversation.id, "Hello")
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_ACCESS_DENIED)
            })

        verify(exactly = 0) { messageRepo.save(any()) }
    }

    @Test
    fun `sendMessage sends to correct recipient when userTwo sends`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messageRepo.save(any()) } returnsArgument 0
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit

        // when
        chatService.sendMessage(userB, conversation.id, "Hello Alice!")

        // then
        verify(exactly = 1) { messagingTemplate.convertAndSendToUser(userA.id, "/queue/messages", any()) }
        verify(exactly = 1) { messagingTemplate.convertAndSendToUser(userB.id, "/queue/messages", any()) }
    }

    @Test
    fun `getUnreadCounts returns map of conversation id to count`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { messageRepo.countUnreadByConversations(listOf(conversation), userA) } returns
            listOf(arrayOf(conversation.id, 3L))

        // when
        val result = chatService.getUnreadCounts(userA, listOf(conversation))

        // then
        assertThat(result).containsExactlyEntriesOf(mapOf(conversation.id to 3L))
    }

    @Test
    fun `getUnreadCounts returns empty map for empty input`() {
        // when
        val result = chatService.getUnreadCounts(UserBuilder().build(), emptyList())

        // then
        assertThat(result).isEmpty()
        verify(exactly = 0) { messageRepo.countUnreadByConversations(any(), any()) }
    }

    @Test
    fun `markConversationRead marks messages read and sends receipt to partner`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messageRepo.markConversationRead(conversation, userA, any()) } returns 2
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit
        val receiptSlot = slot<ReadReceiptResponse>()

        // when
        chatService.markConversationRead(userA, conversation.id)

        // then
        verify(exactly = 1) { messageRepo.markConversationRead(conversation, userA, any()) }
        verify(exactly = 1) {
            messagingTemplate.convertAndSendToUser(userB.id, "/queue/read-receipts", capture(receiptSlot))
        }
        assertThat(receiptSlot.captured.conversationId).isEqualTo(conversation.id)
        assertThat(receiptSlot.captured.readBy).isEqualTo(userA.id)
        assertThat(receiptSlot.captured.readAt).isNotNull()
    }

    @Test
    fun `markConversationRead sends receipt to userOne when userTwo marks read`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messageRepo.markConversationRead(conversation, userB, any()) } returns 1
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit
        val receiptSlot = slot<ReadReceiptResponse>()

        // when
        chatService.markConversationRead(userB, conversation.id)

        // then
        verify(exactly = 1) {
            messagingTemplate.convertAndSendToUser(userA.id, "/queue/read-receipts", capture(receiptSlot))
        }
        assertThat(receiptSlot.captured.readBy).isEqualTo(userB.id)
    }

    @Test
    fun `markConversationRead does not send receipt when no messages were updated`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messageRepo.markConversationRead(conversation, userA, any()) } returns 0

        // when
        chatService.markConversationRead(userA, conversation.id)

        // then
        verify(exactly = 0) { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) }
    }

    @Test
    fun `markConversationRead throws EntryNotFoundException when conversation not found`() {
        // given
        every { conversationRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            chatService.markConversationRead(UserBuilder().build(), "nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_NOT_FOUND)
            })

        verify(exactly = 0) { messageRepo.markConversationRead(any(), any(), any()) }
    }

    @Test
    fun `markConversationRead throws AccessDeniedException when user is not a member`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)

        // then
        assertThatThrownBy {
            chatService.markConversationRead(otherUser, conversation.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_ACCESS_DENIED)
            })

        verify(exactly = 0) { messageRepo.markConversationRead(any(), any(), any()) }
    }

    @Test
    fun `notifyTyping sends typing event to partner`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit
        val typingSlot = slot<TypingResponse>()

        // when
        chatService.notifyTyping(userA, conversation.id)

        // then
        verify(exactly = 1) {
            messagingTemplate.convertAndSendToUser(userB.id, "/queue/typing", capture(typingSlot))
        }
        assertThat(typingSlot.captured.conversationId).isEqualTo(conversation.id)
        assertThat(typingSlot.captured.userId).isEqualTo(userA.id)
    }

    @Test
    fun `notifyTyping throws EntryNotFoundException when conversation not found`() {
        // given
        every { conversationRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            chatService.notifyTyping(UserBuilder().build(), "nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_NOT_FOUND)
            })

        verify(exactly = 0) { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) }
    }

    @Test
    fun `notifyTyping throws AccessDeniedException when user is not a member`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val conversation = ConversationBuilder().build(userOne = userA, userTwo = userB)
        every { conversationRepo.findById(conversation.id) } returns Optional.of(conversation)

        // then
        assertThatThrownBy {
            chatService.notifyTyping(otherUser, conversation.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.CONVERSATION_ACCESS_DENIED)
            })

        verify(exactly = 0) { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) }
    }
}
