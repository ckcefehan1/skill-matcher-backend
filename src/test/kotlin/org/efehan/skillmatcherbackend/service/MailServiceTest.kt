package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import jakarta.mail.internet.MimeMessage
import org.efehan.skillmatcherbackend.config.properties.MailProperties
import org.efehan.skillmatcherbackend.core.mail.MailSender
import org.efehan.skillmatcherbackend.core.mail.SmtpEmailService
import org.efehan.skillmatcherbackend.core.mail.TemplateService
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockKExtension::class)
@DisplayName("Smtp Email Service Unit Tests")
class MailServiceTest {
    @MockK
    private lateinit var javaMailSender: JavaMailSender

    @MockK
    private lateinit var templateService: TemplateService

    @MockK
    private lateinit var mailProperties: MailProperties

    private lateinit var smtpEmailService: SmtpEmailService

    @BeforeEach
    fun setUp() {
        every { mailProperties.from } returns "noreply@skill-matcher.local"
        every { mailProperties.baseUrl } returns "http://localhost:4200"
        smtpEmailService = SmtpEmailService(MailSender(javaMailSender, templateService, mailProperties))
    }

    private fun buildTestUser(): UserModel {
        val role = RoleModel("EMPLOYER", null)
        return UserModel(
            email = "max@firma.de",
            passwordHash = null,
            firstName = "Max",
            lastName = "Mustermann",
            role = role,
        )
    }

    @Test
    fun `should send invitation email with built link and rendered template`() {
        // given
        val user = buildTestUser()
        val mimeMessage = mockk<MimeMessage>(relaxed = true)

        every { javaMailSender.createMimeMessage() } returns mimeMessage
        every {
            templateService.renderInvitation("Max", "http://localhost:4200/invitations/accept?token=abc123", 72L)
        } returns "<html>invitation</html>"
        every { javaMailSender.send(mimeMessage) } returns Unit

        // when
        smtpEmailService.sendInvitationEmail(user, "abc123", 72L)

        // then
        verify(exactly = 1) {
            templateService.renderInvitation("Max", "http://localhost:4200/invitations/accept?token=abc123", 72L)
        }
        verify(exactly = 1) { javaMailSender.send(mimeMessage) }
    }

    @Test
    fun `should send welcome email with rendered template`() {
        // given
        val user = buildTestUser()
        val mimeMessage = mockk<MimeMessage>(relaxed = true)

        every { javaMailSender.createMimeMessage() } returns mimeMessage
        every { templateService.renderWelcome("Max") } returns "<html>welcome</html>"
        every { javaMailSender.send(mimeMessage) } returns Unit

        // when
        smtpEmailService.sendWelcomeEmail(user)

        // then
        verify(exactly = 1) { templateService.renderWelcome("Max") }
        verify(exactly = 1) { javaMailSender.send(mimeMessage) }
    }

    @Test
    fun `should not throw when mail sending fails`() {
        // given
        val user = buildTestUser()
        val mimeMessage = mockk<MimeMessage>(relaxed = true)

        every { javaMailSender.createMimeMessage() } returns mimeMessage
        every { templateService.renderInvitation(any(), any(), any()) } returns "<html>test</html>"
        every { javaMailSender.send(mimeMessage) } throws MailSendException("SMTP connection failed")

        // when - should not throw
        smtpEmailService.sendInvitationEmail(user, "abc123", 72L)
    }

    @Test
    fun `should not throw when template rendering fails`() {
        // given
        val user = buildTestUser()

        every { templateService.renderInvitation(any(), any(), any()) } throws RuntimeException("Template not found")

        // when - should not throw
        smtpEmailService.sendInvitationEmail(user, "abc123", 72L)
    }

    @Test
    fun `should not throw when welcome email sending fails`() {
        // given
        val user = buildTestUser()
        val mimeMessage = mockk<MimeMessage>(relaxed = true)

        every { javaMailSender.createMimeMessage() } returns mimeMessage
        every { templateService.renderWelcome(any()) } returns "<html>welcome</html>"
        every { javaMailSender.send(mimeMessage) } throws MailSendException("SMTP connection failed")

        // when - should not throw
        smtpEmailService.sendWelcomeEmail(user)
    }
}
