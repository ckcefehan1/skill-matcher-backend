package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.project.ProjectService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectMemberBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillModel
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("ProjectService Unit Tests")
class ProjectServiceTest {
    @MockK
    private lateinit var projectRepo: ProjectRepository

    @MockK
    private lateinit var projectSkillRepo: ProjectSkillRepository

    @MockK
    private lateinit var projectMemberRepo: ProjectMemberRepository

    @InjectMockKs
    private lateinit var projectService: ProjectService

    @Test
    fun `createProject saves and returns project with status PLANNED`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.save(any()) } returnsArgument 0

        // when
        val result =
            projectService.createProject(
                owner = owner,
                name = project.name,
                description = project.description,
                startDate = project.startDate,
                endDate = project.endDate,
                maxMembers = project.maxMembers,
            )

        // then
        assertThat(result.name).isEqualTo(project.name)
        assertThat(result.description).isEqualTo(project.description)
        assertThat(result.status).isEqualTo(ProjectStatus.PLANNED)
        assertThat(result.startDate).isEqualTo(project.startDate)
        assertThat(result.endDate).isEqualTo(project.endDate)
        assertThat(result.maxMembers).isEqualTo(project.maxMembers)
        assertThat(result.owner).isEqualTo(owner)
        verify(exactly = 1) { projectRepo.save(any()) }
    }

    @Test
    fun `getProject returns project when found`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // when
        val result = projectService.getProject(project.id)

        // then
        assertThat(result.name).isEqualTo(project.name)
        assertThat(result.owner).isEqualTo(owner)
    }

    @Test
    fun `getProject throws EntryNotFoundException when not found`() {
        // given
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            projectService.getProject("nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
            })
    }

    @Test
    fun `getAllProjects returns all projects`() {
        // given
        val owner = UserBuilder().build()
        val project1 = ProjectBuilder().build(owner = owner)
        val project2 = ProjectBuilder().build(owner = owner, name = "Another Project")
        every { projectRepo.findAll(any<Pageable>()) } returns PageImpl(listOf(project1, project2))

        // when
        val result = projectService.getAllProjects(PageRequest.of(0, 20))

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].name).isEqualTo(project1.name)
        assertThat(result.content[1].name).isEqualTo(project2.name)
    }

    @Test
    fun `getAllProjects returns empty list when no projects exist`() {
        // given
        every { projectRepo.findAll(any<Pageable>()) } returns PageImpl(emptyList())

        // when
        val result = projectService.getAllProjects(PageRequest.of(0, 20))

        // then
        assertThat(result.content).isEmpty()
    }

    @Test
    fun `updateProject updates and returns project when owner`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val updated =
            ProjectBuilder().build(
                owner = owner,
                name = "Updated Name",
                description = "Updated description",
                status = ProjectStatus.ACTIVE,
                startDate = project.startDate.plusMonths(1),
                endDate = project.endDate.plusMonths(3),
                maxMembers = 8,
            )
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectRepo.save(any()) } returnsArgument 0

        // when
        val result =
            projectService.updateProject(
                user = owner,
                projectId = project.id,
                name = updated.name,
                description = updated.description,
                status = updated.status,
                startDate = updated.startDate,
                endDate = updated.endDate,
                maxMembers = updated.maxMembers,
            )

        // then
        assertThat(result.name).isEqualTo(updated.name)
        assertThat(result.description).isEqualTo(updated.description)
        assertThat(result.status).isEqualTo(updated.status)
        assertThat(result.startDate).isEqualTo(updated.startDate)
        assertThat(result.endDate).isEqualTo(updated.endDate)
        assertThat(result.maxMembers).isEqualTo(updated.maxMembers)
        verify(exactly = 1) { projectRepo.save(any()) }
    }

    @Test
    fun `updateProject throws EntryNotFoundException when project not found`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            projectService.updateProject(
                user = owner,
                projectId = "nonexistent",
                name = project.name,
                description = project.description,
                status = project.status,
                startDate = project.startDate,
                endDate = project.endDate,
                maxMembers = project.maxMembers,
            )
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
            })

        verify(exactly = 0) { projectRepo.save(any()) }
    }

    @Test
    fun `updateProject throws AccessDeniedException when not owner`() {
        // given
        val owner = UserBuilder().build()
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy {
            projectService.updateProject(
                user = otherUser,
                projectId = project.id,
                name = project.name,
                description = project.description,
                status = project.status,
                startDate = project.startDate,
                endDate = project.endDate,
                maxMembers = project.maxMembers,
            )
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED)
            })

        verify(exactly = 0) { projectRepo.save(any()) }
    }

    @Test
    fun `deleteProject deletes project and its skills when owner`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val projectMember = ProjectMemberBuilder().build(project = project, user = owner)
        val skill = SkillBuilder().build()
        val projectSkill = ProjectSkillModel(project = project, skill = skill, level = 3)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectMemberRepo.findByProject(project) } returns listOf(projectMember)
        every { projectMemberRepo.deleteAll(listOf(projectMember)) } returns Unit
        every { projectSkillRepo.findByProject(project) } returns listOf(projectSkill)
        every { projectSkillRepo.deleteAll(listOf(projectSkill)) } returns Unit
        every { projectRepo.delete(project) } returns Unit

        // when
        projectService.deleteProject(owner, project.id)

        // then
        verify(exactly = 1) { projectMemberRepo.deleteAll(listOf(projectMember)) }
        verify(exactly = 1) { projectSkillRepo.deleteAll(listOf(projectSkill)) }
        verify(exactly = 1) { projectRepo.delete(project) }
    }

    @Test
    fun `deleteProject works when project has no skills`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectMemberRepo.findByProject(project) } returns emptyList()
        every { projectMemberRepo.deleteAll(emptyList()) } returns Unit
        every { projectSkillRepo.findByProject(project) } returns emptyList()
        every { projectSkillRepo.deleteAll(emptyList()) } returns Unit
        every { projectRepo.delete(project) } returns Unit

        // when
        projectService.deleteProject(owner, project.id)

        // then
        verify(exactly = 1) { projectMemberRepo.deleteAll(emptyList()) }
        verify(exactly = 1) { projectSkillRepo.deleteAll(emptyList()) }
        verify(exactly = 1) { projectRepo.delete(project) }
    }

    @Test
    fun `deleteProject throws EntryNotFoundException when project not found`() {
        // given
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            projectService.deleteProject(UserBuilder().build(), "nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
            })

        verify(exactly = 0) { projectRepo.delete(any<ProjectModel>()) }
    }

    @Test
    fun `deleteProject throws AccessDeniedException when not owner`() {
        // given
        val owner = UserBuilder().build()
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy {
            projectService.deleteProject(otherUser, project.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED)
            })

        verify(exactly = 0) { projectRepo.delete(any<ProjectModel>()) }
    }
}
