package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.project.ProjectService
import org.efehan.skillmatcherbackend.core.projectskill.ProjectSkillService
import org.efehan.skillmatcherbackend.core.skill.SkillService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectSkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.persistence.SkillRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("ProjectSkillService Unit Tests")
class ProjectSkillServiceTest {
    @MockK
    private lateinit var projectRepo: ProjectRepository

    @MockK
    private lateinit var skillRepo: SkillRepository

    @MockK
    private lateinit var projectSkillRepo: ProjectSkillRepository

    private lateinit var projectSkillService: ProjectSkillService

    @BeforeEach
    fun setUp() {
        projectSkillService = ProjectSkillService(ProjectService(projectRepo), SkillService(skillRepo), projectSkillRepo)
    }

    @Test
    fun `addOrUpdateSkill creates new skill and project skill`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val skill = SkillBuilder().build(name = "kotlin")
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { skillRepo.findByNameIgnoreCase("kotlin") } returns skill
        every { projectSkillRepo.findByProjectAndSkillId(project, skill.id) } returns null
        every { projectSkillRepo.save(any()) } returnsArgument 0

        // when
        val (result, created) = projectSkillService.addOrUpdateSkill(owner, project.id, "Kotlin", 3)

        // then
        assertThat(created).isTrue()
        assertThat(result.skill.name).isEqualTo("kotlin")
        assertThat(result.level).isEqualTo(3)
        assertThat(result.priority).isEqualTo(SkillPriority.MUST_HAVE)
        verify(exactly = 1) { projectSkillRepo.save(any()) }
    }

    @Test
    fun `addOrUpdateSkill updates level and priority when project already has the skill`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val skill = SkillBuilder().build(name = "kotlin")
        val existingProjectSkill = ProjectSkillBuilder().build(project = project, skill = skill, level = 2)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { skillRepo.findByNameIgnoreCase("kotlin") } returns skill
        every { projectSkillRepo.findByProjectAndSkillId(project, skill.id) } returns existingProjectSkill
        every { projectSkillRepo.save(any()) } returnsArgument 0

        // when
        val (result, created) = projectSkillService.addOrUpdateSkill(owner, project.id, "Kotlin", 5, "NICE_TO_HAVE")

        // then
        assertThat(created).isFalse()
        assertThat(result.level).isEqualTo(5)
        assertThat(result.priority).isEqualTo(SkillPriority.NICE_TO_HAVE)
        assertThat(existingProjectSkill.priority).isEqualTo(SkillPriority.NICE_TO_HAVE)
    }

    @Test
    fun `addOrUpdateSkill throws when level is below 1`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)

        // then
        assertThatThrownBy {
            projectSkillService.addOrUpdateSkill(owner, project.id, "Kotlin", 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(exactly = 0) { projectSkillRepo.save(any()) }
    }

    @Test
    fun `addOrUpdateSkill throws when level is above 5`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)

        // then
        assertThatThrownBy {
            projectSkillService.addOrUpdateSkill(owner, project.id, "Kotlin", 6)
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(exactly = 0) { projectSkillRepo.save(any()) }
    }

    @Test
    fun `addOrUpdateSkill throws EntryNotFoundException when project not found`() {
        // given
        val owner = UserBuilder().build()
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            projectSkillService.addOrUpdateSkill(owner, "nonexistent", "Kotlin", 3)
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
            })

        verify(exactly = 0) { projectSkillRepo.save(any()) }
    }

    @Test
    fun `addOrUpdateSkill throws AccessDeniedException when user is not project owner`() {
        // given
        val owner = UserBuilder().build()
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy {
            projectSkillService.addOrUpdateSkill(otherUser, project.id, "Kotlin", 3)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED)
            })

        verify(exactly = 0) { projectSkillRepo.save(any()) }
    }

    @Test
    fun `getProjectSkills returns project skill models`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val skill1 = SkillBuilder().build(name = "kotlin")
        val skill2 = SkillBuilder().build(name = "java")
        val projectSkills =
            listOf(
                ProjectSkillBuilder().build(project = project, skill = skill1, level = 4),
                ProjectSkillBuilder().build(project = project, skill = skill2, level = 3, priority = SkillPriority.NICE_TO_HAVE),
            )
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns projectSkills

        // when
        val result = projectSkillService.getProjectSkills(owner, project.id)

        // then
        assertThat(result).hasSize(2)
        assertThat(result[0].skill.name).isEqualTo("kotlin")
        assertThat(result[0].level).isEqualTo(4)
        assertThat(result[0].priority).isEqualTo(SkillPriority.MUST_HAVE)
        assertThat(result[1].skill.name).isEqualTo("java")
        assertThat(result[1].level).isEqualTo(3)
        assertThat(result[1].priority).isEqualTo(SkillPriority.NICE_TO_HAVE)
    }

    @Test
    fun `getProjectSkills returns empty list when project has no skills`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns emptyList()

        // when
        val result = projectSkillService.getProjectSkills(owner, project.id)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getProjectSkills throws AccessDeniedException when user is not project owner`() {
        // given
        val owner = UserBuilder().build()
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy {
            projectSkillService.getProjectSkills(otherUser, project.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED)
            })
    }

    @Test
    fun `deleteSkill deletes project skill successfully`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val skill = SkillBuilder().build(name = "kotlin")
        val projectSkill = ProjectSkillBuilder().build(project = project, skill = skill)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findById(projectSkill.id) } returns Optional.of(projectSkill)
        every { projectSkillRepo.delete(projectSkill) } returns Unit

        // when
        projectSkillService.deleteSkill(owner, project.id, projectSkill.id)

        // then
        verify(exactly = 1) { projectSkillRepo.delete(projectSkill) }
    }

    @Test
    fun `deleteSkill throws EntryNotFoundException when skill not found`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            projectSkillService.deleteSkill(owner, project.id, "nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_SKILL_NOT_FOUND)
            })

        verify(exactly = 0) { projectSkillRepo.delete(any()) }
    }

    @Test
    fun `deleteSkill throws AccessDeniedException when project skill belongs to different project`() {
        // given
        val owner = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val otherOwner = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "PM")
        val otherProject = ProjectBuilder().build(owner = otherOwner, name = "Other Project")
        val skill = SkillBuilder().build(name = "kotlin")
        val otherProjectSkill = ProjectSkillBuilder().build(project = otherProject, skill = skill)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findById(otherProjectSkill.id) } returns Optional.of(otherProjectSkill)

        // then
        assertThatThrownBy {
            projectSkillService.deleteSkill(owner, project.id, otherProjectSkill.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_SKILL_ACCESS_DENIED)
            })

        verify(exactly = 0) { projectSkillRepo.delete(any()) }
    }

    @Test
    fun `deleteSkill throws AccessDeniedException when user is not project owner`() {
        // given
        val owner = UserBuilder().build()
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val skill = SkillBuilder().build(name = "kotlin")
        val projectSkill = ProjectSkillBuilder().build(project = project, skill = skill)
        every { projectRepo.findById(project.id) } returns Optional.of(project)

        // then
        assertThatThrownBy {
            projectSkillService.deleteSkill(otherUser, project.id, projectSkill.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_ACCESS_DENIED)
            })

        verify(exactly = 0) { projectSkillRepo.delete(any()) }
    }
}
