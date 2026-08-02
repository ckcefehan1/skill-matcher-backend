package org.efehan.skillmatcherbackend.service

import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.efehan.skillmatcherbackend.core.mail.MailSender
import org.efehan.skillmatcherbackend.core.mail.rabbit.MailCommand
import org.efehan.skillmatcherbackend.core.mail.rabbit.MailCommandListener
import org.efehan.skillmatcherbackend.core.mail.rabbit.MailEnvelope
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("Mail Command Listener Unit Tests")
class MailCommandListenerTest {
    @MockK(relaxed = true)
    private lateinit var mailSender: MailSender

    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var projectRepository: ProjectRepository

    @InjectMockKs
    private lateinit var listener: MailCommandListener

    private fun user(email: String) = UserBuilder().build(email = email, role = RoleModel("EMPLOYER", null))

    @Test
    fun `sends invitation mail when user exists`() {
        // given
        val user = user("test@example.com")
        io.mockk.every { userService.findById(user.id) } returns user

        // when
        listener.handle(MailEnvelope(null, MailCommand.Invitation(user.id, "token-123", 72)))

        // then
        verify(exactly = 1) { mailSender.sendInvitationEmail(user, "token-123", 72) }
    }

    @Test
    fun `drops invitation mail when user no longer exists`() {
        // given
        io.mockk.every { userService.findById("gone") } returns null

        // when
        listener.handle(MailEnvelope(null, MailCommand.Invitation("gone", "token-123", 72)))

        // then
        verify(exactly = 0) { mailSender.sendInvitationEmail(any(), any(), any()) }
    }

    @Test
    fun `sends registration code mail when user exists`() {
        // given
        val user = user("test@example.com")
        io.mockk.every { userService.findById(user.id) } returns user

        // when
        listener.handle(MailEnvelope(null, MailCommand.RegistrationCode(user.id, "123456", 15)))

        // then
        verify(exactly = 1) { mailSender.sendRegistrationCodeEmail(user, "123456", 15) }
    }

    @Test
    fun `drops registration code mail when user no longer exists`() {
        // given
        io.mockk.every { userService.findById("gone") } returns null

        // when
        listener.handle(MailEnvelope(null, MailCommand.RegistrationCode("gone", "123456", 15)))

        // then
        verify(exactly = 0) { mailSender.sendRegistrationCodeEmail(any(), any(), any()) }
    }

    @Test
    fun `sends application decided mail when entities exist`() {
        // given
        val applicant = user("applicant@example.com")
        val project = mockk<ProjectModel>()
        io.mockk.every { userService.findById(applicant.id) } returns applicant
        io.mockk.every { projectRepository.findById("p1") } returns Optional.of(project)

        // when
        listener.handle(MailEnvelope(null, MailCommand.ApplicationDecided(applicant.id, "p1", true, null)))

        // then
        verify(exactly = 1) { mailSender.sendApplicationDecidedEmail(applicant, project, true, null) }
    }

    @Test
    fun `drops application decided mail when project no longer exists`() {
        // given
        val applicant = user("applicant@example.com")
        io.mockk.every { userService.findById(applicant.id) } returns applicant
        io.mockk.every { projectRepository.findById("gone") } returns Optional.empty()

        // when
        listener.handle(MailEnvelope(null, MailCommand.ApplicationDecided(applicant.id, "gone", true, null)))

        // then
        verify(exactly = 0) { mailSender.sendApplicationDecidedEmail(any(), any(), any(), any()) }
    }

    @Test
    fun `sends project invitation mail when all entities exist`() {
        // given
        val invitee = user("invitee@example.com")
        val pm = user("pm@example.com")
        val project = mockk<ProjectModel>()
        io.mockk.every { userService.findById(invitee.id) } returns invitee
        io.mockk.every { userService.findById(pm.id) } returns pm
        io.mockk.every { projectRepository.findById("p1") } returns Optional.of(project)

        // when
        listener.handle(MailEnvelope(null, MailCommand.ProjectInvitation(invitee.id, pm.id, "p1", "join us")))

        // then
        verify(exactly = 1) { mailSender.sendProjectInvitationEmail(invitee, pm, project, "join us") }
    }
}
