package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.efehan.skillmatcherbackend.TestcontainersConfiguration
import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.efehan.skillmatcherbackend.core.chat.ChatEventPublisher
import org.efehan.skillmatcherbackend.core.chat.ChatService
import org.efehan.skillmatcherbackend.core.chat.RabbitChatEventPublisher
import org.efehan.skillmatcherbackend.persistence.ChatMessageRepository
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.NotificationRepository
import org.efehan.skillmatcherbackend.persistence.NotificationType
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.RabbitMQContainer
import java.time.Duration

/**
 * The default test profile short-circuits chat delivery through
 * DirectChatEventPublisher, which leaves the whole RabbitMQ path — config,
 * publisher, serialization, listener, DLQ — unexecuted. This test forces the
 * broker on and asserts a real publish/consume roundtrip.
 */
@SpringBootTest(properties = ["chat.rabbitmq.enabled=true"])
@Import(TestcontainersConfiguration::class, ChatRabbitIT.RabbitContainerConfiguration::class)
@ActiveProfiles("test")
@DisplayName("Chat RabbitMQ Integration Tests")
class ChatRabbitIT {
    @TestConfiguration(proxyBeanMethods = false)
    class RabbitContainerConfiguration {
        @Bean
        @ServiceConnection
        fun rabbitmq(): RabbitMQContainer = RabbitMQContainer("rabbitmq:4-management")
    }

    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var chatEventPublisher: ChatEventPublisher

    @Autowired
    private lateinit var rabbitAdmin: RabbitAdmin

    @Autowired
    private lateinit var rabbitProperties: RabbitMQProperties

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @BeforeEach
    fun cleanUp() {
        notificationRepository.deleteAll()
        chatMessageRepository.deleteAll()
        conversationRepository.deleteAll()
        userRepository.deleteAll()
        roleRepository.deleteAll()
    }

    private fun createUser(email: String): UserModel {
        val role = roleRepository.findAll().firstOrNull() ?: roleRepository.save(RoleModel("EMPLOYER", null))
        return userRepository.save(
            UserModel(
                email = email,
                passwordHash = passwordEncoder.encode("Secret-Password1!"),
                firstName = "Test",
                lastName = "User",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    private fun dlqDepth(): Int = rabbitAdmin.getQueueInfo(rabbitProperties.chatDlq)?.messageCount?.toInt() ?: 0

    @Test
    fun `should use the RabbitMQ publisher when the broker is enabled`() {
        assertThat(chatEventPublisher).isInstanceOf(RabbitChatEventPublisher::class.java)
    }

    @Test
    fun `should create the notification through a real publish and consume roundtrip`() {
        // given
        val alice = createUser("alice@rabbit.de")
        val bob = createUser("bob@rabbit.de")
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        // when — publishes after commit, travels through the broker, listener inserts the notification
        val message = chatService.sendMessage(alice, conversation.id, "Hallo über RabbitMQ")

        // then
        await.atMost(Duration.ofSeconds(15)).until {
            notificationRepository.existsByUserAndTypeAndReferenceId(bob, NotificationType.CHAT_MESSAGE, message.id)
        }
        assertThat(notificationRepository.findAll()).hasSize(1)
        assertThat(dlqDepth()).isZero()
    }

    @Test
    fun `should not dead letter read receipts`() {
        // given
        val alice = createUser("alice@rabbit.de")
        val bob = createUser("bob@rabbit.de")
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))
        chatService.sendMessage(alice, conversation.id, "Hallo")

        // when — second event type, exercises the other routing key and payload class
        chatService.markConversationRead(bob, conversation.id)

        // then — a deserialization failure retries with backoff and only then lands
        // in the DLQ, so the queue has to stay empty for longer than the retry window
        await
            .during(Duration.ofSeconds(6))
            .atMost(Duration.ofSeconds(20))
            .until { dlqDepth() == 0 }
    }
}
