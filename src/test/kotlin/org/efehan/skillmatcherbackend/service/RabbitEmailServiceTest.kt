package org.efehan.skillmatcherbackend.service

import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.efehan.skillmatcherbackend.core.mail.rabbit.MailCommand
import org.efehan.skillmatcherbackend.core.mail.rabbit.RabbitEmailService
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager

@ExtendWith(MockKExtension::class)
@DisplayName("Rabbit Email Service Unit Tests")
class RabbitEmailServiceTest {
    @MockK(relaxed = true)
    private lateinit var rabbitTemplate: RabbitTemplate

    private val properties =
        RabbitMQProperties(
            exchange = "skill-matcher.events",
            mailQueue = "mail.send",
            mailDlq = "mail.send.dlq",
            mailRoutingKey = "mail.send",
            retry =
                RabbitMQProperties.Retry(
                    maxRetries = 2,
                    initialIntervalMs = 1000,
                    multiplier = 2.0,
                    maxIntervalMs = 5000,
                ),
        )
    private lateinit var rabbitEmailService: RabbitEmailService

    @BeforeEach
    fun setUp() {
        rabbitEmailService = RabbitEmailService(rabbitTemplate, properties)
    }

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun user() = UserBuilder().build(email = "test@example.com", role = RoleModel("EMPLOYER", null))

    @Test
    fun `publishes immediately when no transaction is active`() {
        // given
        val user = user()

        // when
        rabbitEmailService.sendInvitationEmail(user, "token-123", 72)

        // then
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                properties.exchange,
                properties.mailRoutingKey,
                MailCommand.Invitation(user.id, "token-123", 72),
            )
        }
    }

    @Test
    fun `defers publish until after commit when transaction is active`() {
        // given
        val user = user()
        TransactionSynchronizationManager.initSynchronization()

        // when
        rabbitEmailService.sendPasswordResetEmail(user, "reset-token", 24)

        // then - nothing published before commit
        verify(exactly = 0) { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>()) }

        // when - transaction commits
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        // then
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                properties.exchange,
                properties.mailRoutingKey,
                MailCommand.PasswordReset(user.id, "reset-token", 24),
            )
        }
    }

    @Test
    fun `welcome email carries only the user id`() {
        // given
        val user = user()

        // when
        rabbitEmailService.sendWelcomeEmail(user)

        // then
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(
                properties.exchange,
                properties.mailRoutingKey,
                MailCommand.Welcome(user.id),
            )
        }
    }
}
