package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.projectmember.ProjectMemberService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectApplicationBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectMemberBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.RoleBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberModel
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("ProjectMemberService Unit Tests")
class ProjectMemberServiceTest {
    @MockK
    private lateinit var projectRepo: ProjectRepository

    @MockK
    private lateinit var userRepo: UserRepository

    @MockK
    private lateinit var memberRepo: ProjectMemberRepository

    @MockK
    private lateinit var applicationRepo: ProjectApplicationRepository

    @InjectMockKs
    private lateinit var service: ProjectMemberService

    @Test
    fun `addMember saves and returns new member`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de", firstName = "Owner", lastName = "User")
        val member =
            UserBuilder().build(
                email = "member@firma.de",
                firstName = "Member",
                lastName = "User",
                role = RoleBuilder().build(name = "EMPLOYER"),
            )
        val project = ProjectBuilder().build(owner = owner)
        val acceptedApplication = ProjectApplicationBuilder().build(project = project, user = member, status = ApplicationStatus.ACCEPTED)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { applicationRepo.findByProjectAndUserAndStatus(project, member, ApplicationStatus.ACCEPTED) } returns acceptedApplication
        every { memberRepo.findByProjectAndUser(project, member) } returns null
        every { memberRepo.countByProjectAndStatus(project, ProjectMemberStatus.ACTIVE) } returns 2
        every { memberRepo.save(any()) } returnsArgument 0

        // when
        val result = service.addMember(owner, project.id, member.id)

        // then
        assertThat(result.user.id).isEqualTo(member.id)
        assertThat(result.status).isEqualTo(ProjectMemberStatus.ACTIVE)
        assertThat(result.user.firstName).isEqualTo(member.firstName)
        assertThat(result.user.lastName).isEqualTo(member.lastName)
        verify(exactly = 1) { memberRepo.save(any()) }
    }

    @Test
    fun `addMember throws AccessDeniedException when caller is not owner`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de", firstName = "Owner", lastName = "User")
        val member =
            UserBuilder().build(
                email = "member@firma.de",
                firstName = "Member",
                lastName = "User",
                role = RoleBuilder().build(name = "EMPLOYER"),
            )
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy { service.addMember(member, project.id, member.id) }
            .isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED)
            })
    }

    @Test
    fun `addMember throws EntryNotFoundException when project not found`() {
        // given
        val owner = UserBuilder().build()
        val member = UserBuilder().build(email = "member@firma.de")
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy { service.addMember(owner, "nonexistent", member.id) }
            .isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
            })
    }

    @Test
    fun `addMember throws EntryNotFoundException when user not found`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy { service.addMember(owner, project.id, "nonexistent") }
            .isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.USER_NOT_FOUND)
            })
    }

    @Test
    fun `addMember throws DuplicateEntryException when user is already active member`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        val existingMember = ProjectMemberBuilder().build(project = project, user = member)
        val acceptedApplication = ProjectApplicationBuilder().build(project = project, user = member, status = ApplicationStatus.ACCEPTED)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { applicationRepo.findByProjectAndUserAndStatus(project, member, ApplicationStatus.ACCEPTED) } returns acceptedApplication
        every { memberRepo.findByProjectAndUser(project, member) } returns existingMember

        // then
        assertThatThrownBy { service.addMember(owner, project.id, member.id) }
            .isInstanceOf(DuplicateEntryException::class.java)
            .satisfies({ ex ->
                val e = ex as DuplicateEntryException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_MEMBER_DUPLICATE)
            })
    }

    @Test
    fun `addMember throws AccessDeniedException without accepted application`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { applicationRepo.findByProjectAndUserAndStatus(project, member, ApplicationStatus.ACCEPTED) } returns null

        // then
        assertThatThrownBy { service.addMember(owner, project.id, member.id) }
            .isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_MEMBER_REQUIRES_ACCEPTED_APPLICATION)
            })
    }

    @Test
    fun `addMember reactivates LEFT member`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        val leftMember = ProjectMemberBuilder().build(project = project, user = member, status = ProjectMemberStatus.LEFT)
        val acceptedApplication = ProjectApplicationBuilder().build(project = project, user = member, status = ApplicationStatus.ACCEPTED)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { applicationRepo.findByProjectAndUserAndStatus(project, member, ApplicationStatus.ACCEPTED) } returns acceptedApplication
        every { memberRepo.findByProjectAndUser(project, member) } returns leftMember

        val savedSlot = slot<ProjectMemberModel>()
        every { memberRepo.save(capture(savedSlot)) } returnsArgument 0

        // when
        val result = service.addMember(owner, project.id, member.id)

        // then
        assertThat(result.status).isEqualTo(ProjectMemberStatus.ACTIVE)
        assertThat(savedSlot.captured.status).isEqualTo(ProjectMemberStatus.ACTIVE)
    }

    @Test
    fun `addMember throws when project is full`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val fullProject = ProjectBuilder().build(name = "Full Project", description = "Full", maxMembers = 2, owner = owner)
        val acceptedApplication =
            ProjectApplicationBuilder().build(project = fullProject, user = member, status = ApplicationStatus.ACCEPTED)
        every { projectRepo.findById(fullProject.id) } returns Optional.of(fullProject)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { applicationRepo.findByProjectAndUserAndStatus(fullProject, member, ApplicationStatus.ACCEPTED) } returns acceptedApplication
        every { memberRepo.findByProjectAndUser(fullProject, member) } returns null
        every { memberRepo.countByProjectAndStatus(fullProject, ProjectMemberStatus.ACTIVE) } returns 2

        // then
        assertThatThrownBy { service.addMember(owner, fullProject.id, member.id) }
            .isInstanceOf(DuplicateEntryException::class.java)
            .satisfies({ ex ->
                val e = ex as DuplicateEntryException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_FULL)
            })
    }

    @Test
    fun `getMembers returns only active members`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        val activeMember = ProjectMemberBuilder().build(project = project, user = member)
        val leftMember = ProjectMemberBuilder().build(project = project, user = owner, status = ProjectMemberStatus.LEFT)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { memberRepo.findByProject(project) } returns listOf(activeMember, leftMember)

        // when
        val result = service.getMembers(project.id)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].user.id).isEqualTo(member.id)
    }

    @Test
    fun `getMembers throws when project not found`() {
        // given
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy { service.getMembers("nonexistent") }
            .isInstanceOf(EntryNotFoundException::class.java)
    }

    @Test
    fun `removeMember sets status to LEFT`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        val activeMember = ProjectMemberBuilder().build(project = project, user = member)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { memberRepo.findByProjectAndUser(project, member) } returns activeMember

        val savedSlot = slot<ProjectMemberModel>()
        every { memberRepo.save(capture(savedSlot)) } returnsArgument 0

        // when
        service.removeMember(owner, project.id, member.id)

        // then
        assertThat(savedSlot.captured.status).isEqualTo(ProjectMemberStatus.LEFT)
    }

    @Test
    fun `removeMember throws AccessDeniedException when caller is not owner`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy { service.removeMember(member, project.id, member.id) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `removeMember throws EntryNotFoundException when member not found`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { userRepo.findById(member.id) } returns Optional.of(member)
        every { memberRepo.findByProjectAndUser(project, member) } returns null

        // then
        assertThatThrownBy { service.removeMember(owner, project.id, member.id) }
            .isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_MEMBER_NOT_FOUND)
            })
    }

    @Test
    fun `leaveProject sets own membership to LEFT`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        val activeMember = ProjectMemberBuilder().build(project = project, user = member)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { memberRepo.findByProjectAndUser(project, member) } returns activeMember

        val savedSlot = slot<ProjectMemberModel>()
        every { memberRepo.save(capture(savedSlot)) } returnsArgument 0

        // when
        service.leaveProject(member, project.id)

        // then
        assertThat(savedSlot.captured.status).isEqualTo(ProjectMemberStatus.LEFT)
    }

    @Test
    fun `leaveProject throws EntryNotFoundException when not a member`() {
        // given
        val owner = UserBuilder().build(email = "owner@firma.de")
        val member = UserBuilder().build(email = "member@firma.de")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { memberRepo.findByProjectAndUser(project, member) } returns null

        // then
        assertThatThrownBy { service.leaveProject(member, project.id) }
            .isInstanceOf(EntryNotFoundException::class.java)
    }
}
