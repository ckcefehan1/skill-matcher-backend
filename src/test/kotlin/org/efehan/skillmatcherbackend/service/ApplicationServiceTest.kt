package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.application.ApplicationService
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.core.projectmember.ProjectMemberService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectApplicationBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectMemberBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant

@ExtendWith(MockKExtension::class)
@DisplayName("ApplicationService Unit Tests")
class ApplicationServiceTest {
    @MockK
    private lateinit var applicationRepo: ProjectApplicationRepository

    @MockK
    private lateinit var projectRepo: ProjectRepository

    @MockK
    private lateinit var memberRepo: ProjectMemberRepository

    @MockK
    private lateinit var userRepo: UserRepository

    @MockK
    private lateinit var memberService: ProjectMemberService

    @MockK
    private lateinit var emailService: EmailService

    @MockK(relaxed = true)
    private lateinit var auditService: AuditService

    private lateinit var service: ApplicationService

    private val pm = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
    private val employer = UserBuilder().build(email = "emp@firma.de", firstName = "Emp", lastName = "User")
    private val project = ProjectBuilder().build(owner = pm)

    @BeforeEach
    fun setUp() {
        service =
            ApplicationService(
                applicationRepo = applicationRepo,
                projectRepo = projectRepo,
                memberRepo = memberRepo,
                userRepo = userRepo,
                memberService = memberService,
                emailService = emailService,
                auditService = auditService,
            )
        every { emailService.sendApplicationSubmittedEmail(any(), any(), any(), any()) } just runs
        every { emailService.sendApplicationDecidedEmail(any(), any(), any(), any()) } just runs
        every { emailService.sendProjectInvitationEmail(any(), any(), any(), any()) } just runs
        every { emailService.sendProjectInvitationResponseEmail(any(), any(), any(), any()) } just runs
        every { applicationRepo.findByProjectAndUserAndStatus(any(), any(), ApplicationStatus.INVITED) } returns null
    }

    // --- apply ---

    @Test
    fun `apply creates PENDING application and notifies PM`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { memberRepo.findByProjectAndUser(project, employer) } returns null
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns null
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.apply(employer, project.id, "I'd like to join")

        assertThat(result.status).isEqualTo(ApplicationStatus.PENDING)
        assertThat(result.message).isEqualTo("I'd like to join")
        assertThat(result.user.id).isEqualTo(employer.id)
        verify { emailService.sendApplicationSubmittedEmail(pm, employer, project, "I'd like to join") }
    }

    @Test
    fun `apply throws PROJECT_NOT_FOUND when project does not exist`() {
        every { projectRepo.findByIdOrNull("nope") } returns null

        val ex = assertThrows<EntryNotFoundException> { service.apply(employer, "nope", null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
    }

    @Test
    fun `apply throws APPLICATION_FOR_MEMBER when user is already an active member`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        val activeMember = ProjectMemberBuilder().build(project = project, user = employer, status = ProjectMemberStatus.ACTIVE)
        every { memberRepo.findByProjectAndUser(project, employer) } returns activeMember

        val ex = assertThrows<DuplicateEntryException> { service.apply(employer, project.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_FOR_MEMBER)
    }

    @Test
    fun `apply allows re-application after LEFT membership`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        val leftMember = ProjectMemberBuilder().build(project = project, user = employer, status = ProjectMemberStatus.LEFT)
        every { memberRepo.findByProjectAndUser(project, employer) } returns leftMember
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns null
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.apply(employer, project.id, null)

        assertThat(result.status).isEqualTo(ApplicationStatus.PENDING)
    }

    @Test
    fun `apply throws APPLICATION_DUPLICATE when PENDING application already exists`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { memberRepo.findByProjectAndUser(project, employer) } returns null
        val existing = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns existing

        val ex = assertThrows<DuplicateEntryException> { service.apply(employer, project.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_DUPLICATE)
    }

    // --- accept ---

    @Test
    fun `accept sets status ACCEPTED and notifies applicant without adding member`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByIdOrNull(application.id) } returns application
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.accept(pm, application.id)

        assertThat(result.status).isEqualTo(ApplicationStatus.ACCEPTED)
        assertThat(result.decidedBy?.id).isEqualTo(pm.id)
        assertThat(result.decidedAt).isNotNull()
        verify(exactly = 0) { memberRepo.save(any()) }
        verify { emailService.sendApplicationDecidedEmail(employer, project, true, null) }
    }

    @Test
    fun `accept throws APPLICATION_NOT_FOUND when application does not exist`() {
        every { applicationRepo.findByIdOrNull("nope") } returns null

        val ex = assertThrows<EntryNotFoundException> { service.accept(pm, "nope") }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_NOT_FOUND)
    }

    @Test
    fun `accept throws APPLICATION_ACCESS_DENIED when PM is not the project owner`() {
        val otherPm = UserBuilder().build(email = "other-pm@firma.de", firstName = "Other", lastName = "PM")
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<AccessDeniedException> { service.accept(otherPm, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED)
    }

    @Test
    fun `accept throws APPLICATION_ALREADY_DECIDED when application is not PENDING`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.ACCEPTED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<DuplicateEntryException> { service.accept(pm, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ALREADY_DECIDED)
    }

    // --- decline ---

    @Test
    fun `decline sets status DECLINED and notifies applicant with reason`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByIdOrNull(application.id) } returns application
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.decline(pm, application.id, "Not enough experience")

        assertThat(result.status).isEqualTo(ApplicationStatus.DECLINED)
        assertThat(result.message).isEqualTo("Not enough experience")
        verify { emailService.sendApplicationDecidedEmail(employer, project, false, "Not enough experience") }
    }

    // --- withdraw ---

    @Test
    fun `withdraw sets status WITHDRAWN for own pending application`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByIdOrNull(application.id) } returns application
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.withdraw(employer, application.id)

        assertThat(result.status).isEqualTo(ApplicationStatus.WITHDRAWN)
        assertThat(result.decidedAt).isNotNull()
    }

    @Test
    fun `withdraw throws APPLICATION_ACCESS_DENIED when user is not the applicant`() {
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<AccessDeniedException> { service.withdraw(otherUser, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED)
    }

    @Test
    fun `withdraw throws APPLICATION_ALREADY_DECIDED when application is not PENDING`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.DECLINED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<DuplicateEntryException> { service.withdraw(employer, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ALREADY_DECIDED)
    }

    // --- listForProject ---

    @Test
    fun `listForProject returns applications sorted PENDING first`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        val pendingApp =
            ProjectApplicationBuilder().build(
                project = project,
                user = employer,
                status = ApplicationStatus.PENDING,
                appliedAt = Instant.now().plusSeconds(60),
            )
        val decidedApp =
            ProjectApplicationBuilder().build(
                project = project,
                user = employer,
                status = ApplicationStatus.DECLINED,
                appliedAt = Instant.now(),
            )
        every { applicationRepo.findByProjectPendingFirst(project, any()) } returns PageImpl(listOf(pendingApp, decidedApp))

        val result = service.listForProject(pm, project.id, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].status).isEqualTo(ApplicationStatus.PENDING)
        assertThat(result.content[1].status).isEqualTo(ApplicationStatus.DECLINED)
    }

    @Test
    fun `listForProject throws APPLICATION_ACCESS_DENIED when PM is not the owner`() {
        val otherPm = UserBuilder().build(email = "other-pm@firma.de", firstName = "Other", lastName = "PM")
        every { projectRepo.findByIdOrNull(project.id) } returns project

        val ex =
            assertThrows<AccessDeniedException> {
                service.listForProject(otherPm, project.id, PageRequest.of(0, 20))
            }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED)
    }

    // --- listForUser ---

    @Test
    fun `listForUser returns applications sorted by appliedAt descending`() {
        val older = ProjectApplicationBuilder().build(project = project, user = employer, appliedAt = Instant.now().minusSeconds(3600))
        val newer = ProjectApplicationBuilder().build(project = project, user = employer, appliedAt = Instant.now())
        every { applicationRepo.findByUser(employer, any()) } returns PageImpl(listOf(newer, older))

        val result = service.listForUser(employer, PageRequest.of(0, 20))

        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].appliedAt).isAfter(result.content[1].appliedAt)
    }

    // --- invite ---

    @Test
    fun `invite creates INVITED application and notifies invitee`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { userRepo.findByIdOrNull(employer.id) } returns employer
        every { memberRepo.findByProjectAndUser(project, employer) } returns null
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns null
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.invite(pm, project.id, employer.id, "Join us")

        assertThat(result.status).isEqualTo(ApplicationStatus.INVITED)
        assertThat(result.message).isEqualTo("Join us")
        assertThat(result.user.id).isEqualTo(employer.id)
        verify { emailService.sendProjectInvitationEmail(employer, pm, project, "Join us") }
    }

    @Test
    fun `invite throws APPLICATION_ACCESS_DENIED when PM is not the project owner`() {
        val otherPm = UserBuilder().build(email = "other-pm@firma.de", firstName = "Other", lastName = "PM")
        every { projectRepo.findByIdOrNull(project.id) } returns project

        val ex = assertThrows<AccessDeniedException> { service.invite(otherPm, project.id, employer.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED)
    }

    @Test
    fun `invite throws USER_NOT_FOUND when user does not exist`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { userRepo.findByIdOrNull("nope") } returns null

        val ex = assertThrows<EntryNotFoundException> { service.invite(pm, project.id, "nope", null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.USER_NOT_FOUND)
    }

    @Test
    fun `invite throws APPLICATION_FOR_MEMBER when user is already an active member`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { userRepo.findByIdOrNull(employer.id) } returns employer
        val activeMember = ProjectMemberBuilder().build(project = project, user = employer, status = ProjectMemberStatus.ACTIVE)
        every { memberRepo.findByProjectAndUser(project, employer) } returns activeMember

        val ex = assertThrows<DuplicateEntryException> { service.invite(pm, project.id, employer.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_FOR_MEMBER)
    }

    @Test
    fun `invite throws APPLICATION_DUPLICATE when PENDING application exists`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { userRepo.findByIdOrNull(employer.id) } returns employer
        every { memberRepo.findByProjectAndUser(project, employer) } returns null
        val existing = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns existing

        val ex = assertThrows<DuplicateEntryException> { service.invite(pm, project.id, employer.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_DUPLICATE)
    }

    @Test
    fun `invite throws APPLICATION_DUPLICATE when INVITED application exists`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { userRepo.findByIdOrNull(employer.id) } returns employer
        every { memberRepo.findByProjectAndUser(project, employer) } returns null
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns null
        val existing = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.INVITED) } returns existing

        val ex = assertThrows<DuplicateEntryException> { service.invite(pm, project.id, employer.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_DUPLICATE)
    }

    @Test
    fun `apply throws APPLICATION_DUPLICATE when INVITED application exists`() {
        every { projectRepo.findByIdOrNull(project.id) } returns project
        every { memberRepo.findByProjectAndUser(project, employer) } returns null
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.PENDING) } returns null
        val existing = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByProjectAndUserAndStatus(project, employer, ApplicationStatus.INVITED) } returns existing

        val ex = assertThrows<DuplicateEntryException> { service.apply(employer, project.id, null) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_DUPLICATE)
    }

    // --- acceptInvitation ---

    @Test
    fun `acceptInvitation sets ACCEPTED, adds member via owner and notifies PM`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application
        every { memberService.addMember(pm, project.id, employer.id) } returns
            ProjectMemberBuilder().build(project = project, user = employer)
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.acceptInvitation(employer, application.id)

        assertThat(result.status).isEqualTo(ApplicationStatus.ACCEPTED)
        assertThat(result.decidedBy?.id).isEqualTo(employer.id)
        assertThat(result.decidedAt).isNotNull()
        verify { memberService.addMember(pm, project.id, employer.id) }
        verify { emailService.sendProjectInvitationResponseEmail(pm, employer, project, true) }
    }

    @Test
    fun `acceptInvitation throws APPLICATION_ACCESS_DENIED when user is not the invitee`() {
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<AccessDeniedException> { service.acceptInvitation(otherUser, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED)
    }

    @Test
    fun `acceptInvitation throws APPLICATION_ALREADY_DECIDED when application is not INVITED`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<DuplicateEntryException> { service.acceptInvitation(employer, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ALREADY_DECIDED)
    }

    @Test
    fun `acceptInvitation propagates PROJECT_FULL and does not save`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application
        every { memberService.addMember(pm, project.id, employer.id) } throws
            DuplicateEntryException(
                resource = "ProjectMember",
                field = "projectId",
                value = project.id,
                errorCode = GlobalErrorCode.PROJECT_FULL,
                status = org.springframework.http.HttpStatus.CONFLICT,
            )

        val ex = assertThrows<DuplicateEntryException> { service.acceptInvitation(employer, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.PROJECT_FULL)
        verify(exactly = 0) { applicationRepo.save(any()) }
        verify(exactly = 0) { emailService.sendProjectInvitationResponseEmail(any(), any(), any(), any()) }
    }

    // --- declineInvitation ---

    @Test
    fun `declineInvitation sets DECLINED and notifies PM`() {
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application
        every { applicationRepo.save(any()) } returnsArgument 0

        val result = service.declineInvitation(employer, application.id)

        assertThat(result.status).isEqualTo(ApplicationStatus.DECLINED)
        assertThat(result.decidedBy?.id).isEqualTo(employer.id)
        verify { emailService.sendProjectInvitationResponseEmail(pm, employer, project, false) }
    }

    @Test
    fun `declineInvitation throws APPLICATION_ACCESS_DENIED when user is not the invitee`() {
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val application = ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.INVITED)
        every { applicationRepo.findByIdOrNull(application.id) } returns application

        val ex = assertThrows<AccessDeniedException> { service.declineInvitation(otherUser, application.id) }
        assertThat(ex.errorCode).isEqualTo(GlobalErrorCode.APPLICATION_ACCESS_DENIED)
    }
}
