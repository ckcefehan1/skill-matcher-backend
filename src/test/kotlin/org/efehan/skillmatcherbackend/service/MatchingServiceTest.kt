package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.config.properties.CapacityMode
import org.efehan.skillmatcherbackend.config.properties.MatchingProperties
import org.efehan.skillmatcherbackend.core.matching.MatchTier
import org.efehan.skillmatcherbackend.core.matching.MatchingService
import org.efehan.skillmatcherbackend.core.skill.SkillGraphService
import org.efehan.skillmatcherbackend.core.skill.SkillRelationInfo
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectApplicationBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectSkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserAvailabilityBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserSkillBuilder
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType
import org.efehan.skillmatcherbackend.persistence.UserAvailabilityRepository
import org.efehan.skillmatcherbackend.persistence.UserMemberCount
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("MatchingService Unit Tests")
class MatchingServiceTest {
    @MockK
    private lateinit var projectRepo: ProjectRepository

    @MockK
    private lateinit var projectSkillRepo: ProjectSkillRepository

    @MockK
    private lateinit var userSkillRepo: UserSkillRepository

    @MockK
    private lateinit var availabilityRepo: UserAvailabilityRepository

    @MockK
    private lateinit var projectMemberRepo: ProjectMemberRepository

    @MockK
    private lateinit var applicationRepo: ProjectApplicationRepository

    @MockK
    private lateinit var skillGraphService: SkillGraphService

    private val matchingProperties = MatchingProperties()

    private lateinit var matchingService: MatchingService

    @BeforeEach
    fun setUp() {
        matchingService =
            MatchingService(
                projectRepo = projectRepo,
                projectSkillRepo = projectSkillRepo,
                userSkillRepo = userSkillRepo,
                availabilityRepo = availabilityRepo,
                projectMemberRepo = projectMemberRepo,
                matchingProperties = matchingProperties,
                skillGraphService = skillGraphService,
                applicationRepo = applicationRepo,
            )
        every { projectMemberRepo.countActiveByUserIn(any(), any()) } returns emptyList()
        every { skillGraphService.expandSkills(any()) } returns emptyMap()
        every { applicationRepo.findByProjectAndUserInAndStatus(any(), any(), any()) } returns emptyList()
        every { applicationRepo.findByUserAndProjectIn(any(), any()) } returns emptyList()
    }

    private fun serviceWith(props: MatchingProperties): MatchingService =
        MatchingService(
            projectRepo,
            projectSkillRepo,
            userSkillRepo,
            availabilityRepo,
            projectMemberRepo,
            props,
            skillGraphService,
            applicationRepo,
        )

    @Test
    fun `findCandidatesForProject throws EntryNotFoundException when project not found`() {
        every { projectRepo.findById("nonexistent") } returns Optional.empty()

        assertThatThrownBy {
            matchingService.findCandidatesForProject("nonexistent", 0.0, 20)
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PROJECT_NOT_FOUND)
            })
    }

    @Test
    fun `findCandidatesForProject returns empty list when project has no skills`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findCandidatesForProject returns empty list when no candidates match query`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findCandidatesForProject returns candidates sorted by score descending`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val user2 = UserBuilder().build(email = "user2@firma.de", firstName = "User", lastName = "Two")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 2, priority = SkillPriority.NICE_TO_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)
        val us1Spring = UserSkillBuilder().build(user = user1, skill = spring, level = 3)
        val us2Kotlin = UserSkillBuilder().build(user = user2, skill = kotlin, level = 3)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(us1Kotlin, us1Spring, us2Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(2)
        assertThat(result[0].userId).isEqualTo(user1.id)
        assertThat(result[1].userId).isEqualTo(user2.id)
        assertThat(result[0].score).isGreaterThanOrEqualTo(result[1].score)
    }

    @Test
    fun `findCandidatesForProject excludes active project members via query result`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user2 = UserBuilder().build(email = "user2@firma.de", firstName = "User", lastName = "Two")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us2Kotlin = UserSkillBuilder().build(user = user2, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us2Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].userId).isEqualTo(user2.id)
    }

    @Test
    fun `findCandidatesForProject includes left members when query returns them`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].userId).isEqualTo(user1.id)
    }

    @Test
    fun `findCandidatesForProject filters candidates below minScore`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val user2 = UserBuilder().build(email = "user2@firma.de", firstName = "User", lastName = "Two")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 2, priority = SkillPriority.NICE_TO_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)
        val us1Spring = UserSkillBuilder().build(user = user1, skill = spring, level = 3)
        val us2Spring = UserSkillBuilder().build(user = user2, skill = spring, level = 1)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(us1Kotlin, us1Spring, us2Spring)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.8, 20)

        assertThat(result).allSatisfy { assertThat(it.score).isGreaterThanOrEqualTo(0.8) }
        assertThat(result.map { it.userId }).doesNotContain(user2.id)
    }

    @Test
    fun `findCandidatesForProject respects limit parameter`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val user2 = UserBuilder().build(email = "user2@firma.de", firstName = "User", lastName = "Two")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)
        val us2Kotlin = UserSkillBuilder().build(user = user2, skill = kotlin, level = 3)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(us1Kotlin, us2Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 1)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `findCandidatesForProject calculates full must-have coverage when all fulfilled`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 2, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 3)
        val us1Spring = UserSkillBuilder().build(user = user1, skill = spring, level = 2)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(us1Kotlin, us1Spring)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.mustHaveCoverage).isEqualTo(1.0)
    }

    @Test
    fun `findCandidatesForProject calculates partial must-have coverage`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 4, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 3)
        val us1Spring = UserSkillBuilder().build(user = user1, skill = spring, level = 2)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(us1Kotlin, us1Spring)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.mustHaveCoverage).isEqualTo(0.5)
    }

    @Test
    fun `findCandidatesForProject reports missing skills when user lacks a skill entirely`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val docker = SkillBuilder().build(name = "docker")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psDocker = ProjectSkillBuilder().build(project = project, skill = docker, level = 2, priority = SkillPriority.NICE_TO_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 3)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psDocker)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].missingSkills).hasSize(1)
        assertThat(result[0].missingSkills[0].skillName).isEqualTo("docker")
        assertThat(result[0].missingSkills[0].priority).isEqualTo("NICE_TO_HAVE")
        assertThat(result[0].matchedSkills).hasSize(1)
        assertThat(result[0].matchedSkills[0].skillName).isEqualTo("kotlin")
    }

    @Test
    fun `findCandidatesForProject lists skill as matched even when user level is below required`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 5, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 2)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].matchedSkills).hasSize(1)
        assertThat(result[0].matchedSkills[0].userLevel).isEqualTo(2)
        assertThat(result[0].matchedSkills[0].requiredLevel).isEqualTo(5)
        assertThat(result[0].missingSkills).isEmpty()
        assertThat(result[0].breakdown.mustHaveCoverage).isEqualTo(0.0)
    }

    @Test
    fun `findCandidatesForProject calculates nice-to-have coverage correctly`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val docker = SkillBuilder().build(name = "docker")
        val react = SkillBuilder().build(name = "react")
        val psDocker = ProjectSkillBuilder().build(project = project, skill = docker, level = 2, priority = SkillPriority.NICE_TO_HAVE)
        val psReact = ProjectSkillBuilder().build(project = project, skill = react, level = 3, priority = SkillPriority.NICE_TO_HAVE)
        val us1Docker = UserSkillBuilder().build(user = user1, skill = docker, level = 1)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psDocker, psReact)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Docker)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.niceToHaveCoverage).isEqualTo(0.5)
    }

    @Test
    fun `findCandidatesForProject calculates level fit score with overfit cap`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 5)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.levelFitScore).isEqualTo(1.0)
    }

    @Test
    fun `findCandidatesForProject calculates availability score for partial coverage`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 3)
        val availability =
            UserAvailabilityBuilder().build(
                user = user1,
                availableFrom = LocalDate.of(2026, 3, 1),
                availableTo = LocalDate.of(2026, 6, 1),
            )

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns listOf(availability)

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.availabilityScore).isGreaterThan(0.0)
        assertThat(result[0].breakdown.availabilityScore).isLessThan(1.0)
    }

    @Test
    fun `findCandidatesForProject returns availability 1 when user has no availability entries`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 3)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.availabilityScore).isEqualTo(1.0)
    }

    @Test
    fun `findProjectsForUser returns empty list when user has no skills`() {
        val user = UserBuilder().build()
        every { userSkillRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findProjectsForUser excludes active memberships via query result`() {
        val user = UserBuilder().build()
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 4)
        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findProjectsForUser returns matching projects sorted by score`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project1 = ProjectBuilder().build(name = "Test Project", description = "Test", owner = owner)
        val project2 =
            ProjectBuilder().build(name = "Other Project", description = "Other", owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val docker = SkillBuilder().build(name = "docker")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 4)
        val usSpring = UserSkillBuilder().build(user = user, skill = spring, level = 3)
        val ps1Kotlin = ProjectSkillBuilder().build(project = project1, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val ps1Spring = ProjectSkillBuilder().build(project = project1, skill = spring, level = 2, priority = SkillPriority.MUST_HAVE)
        val ps2Docker = ProjectSkillBuilder().build(project = project2, skill = docker, level = 3, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin, usSpring)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project1, project2)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(ps1Kotlin, ps1Spring, ps2Docker)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(2)
        assertThat(result[0].score).isGreaterThanOrEqualTo(result[1].score)
        assertThat(result[0].projectName).isEqualTo("Test Project")
    }

    @Test
    fun `findProjectsForUser skips projects with no skills`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 3)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns emptyList()
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findProjectsForUser filters projects below minScore`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val docker = SkillBuilder().build(name = "docker")
        val usDocker = UserSkillBuilder().build(user = user, skill = docker, level = 2)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 4, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usDocker)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.9, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findProjectsForUser respects limit parameter`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project1 = ProjectBuilder().build(owner = owner)
        val project2 =
            ProjectBuilder().build(name = "Project 2", description = "Desc", status = ProjectStatus.ACTIVE, owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 4)
        val ps1Kotlin = ProjectSkillBuilder().build(project = project1, skill = kotlin, level = 2, priority = SkillPriority.MUST_HAVE)
        val ps2Kotlin = ProjectSkillBuilder().build(project = project2, skill = kotlin, level = 2, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project1, project2)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(ps1Kotlin, ps2Kotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 1)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `findProjectsForUser returns correct project metadata in result`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(name = "Test Project", description = "Test", owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 3)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 2, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].projectId).isEqualTo(project.id)
        assertThat(result[0].projectName).isEqualTo("Test Project")
        assertThat(result[0].projectDescription).isEqualTo("Test")
        assertThat(result[0].status).isEqualTo("PLANNED")
        assertThat(result[0].ownerName).isEqualTo("PM User")
    }

    // --- Phase 1: Tier classification ---

    @Test
    fun `findCandidatesForProject assigns EXACT tier when must-have coverage is 1`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].matchTier).isEqualTo("EXACT")
    }

    @Test
    fun `findCandidatesForProject assigns STRETCH tier when must-have coverage below threshold`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].breakdown.mustHaveCoverage).isEqualTo(0.5)
        assertThat(result[0].matchTier).isEqualTo("STRETCH")
    }

    @Test
    fun `findCandidatesForProject with tier EXACT returns only candidates with full must-have coverage`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val exactUser = UserBuilder().build(email = "exact@firma.de", firstName = "Exact", lastName = "User")
        val stretchUser = UserBuilder().build(email = "stretch@firma.de", firstName = "Stretch", lastName = "User")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 3, priority = SkillPriority.MUST_HAVE)
        val usExactKotlin = UserSkillBuilder().build(user = exactUser, skill = kotlin, level = 4)
        val usExactSpring = UserSkillBuilder().build(user = exactUser, skill = spring, level = 4)
        val usStretchKotlin = UserSkillBuilder().build(user = stretchUser, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(usExactKotlin, usExactSpring, usStretchKotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20, MatchTier.EXACT)

        assertThat(result).hasSize(1)
        assertThat(result[0].userId).isEqualTo(exactUser.id)
        assertThat(result[0].matchTier).isEqualTo("EXACT")
    }

    @Test
    fun `findCandidatesForProject with tier STRETCH returns only candidates below threshold`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val exactUser = UserBuilder().build(email = "exact@firma.de", firstName = "Exact", lastName = "User")
        val stretchUser = UserBuilder().build(email = "stretch@firma.de", firstName = "Stretch", lastName = "User")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val spring = SkillBuilder().build(name = "spring boot")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psSpring = ProjectSkillBuilder().build(project = project, skill = spring, level = 3, priority = SkillPriority.MUST_HAVE)
        val usExactKotlin = UserSkillBuilder().build(user = exactUser, skill = kotlin, level = 4)
        val usExactSpring = UserSkillBuilder().build(user = exactUser, skill = spring, level = 4)
        val usStretchKotlin = UserSkillBuilder().build(user = stretchUser, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psSpring)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(usExactKotlin, usExactSpring, usStretchKotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20, MatchTier.STRETCH)

        assertThat(result).hasSize(1)
        assertThat(result[0].userId).isEqualTo(stretchUser.id)
        assertThat(result[0].matchTier).isEqualTo("STRETCH")
    }

    // --- Phase 1: Capacity ---

    @Test
    fun `findCandidatesForProject excludes user at capacity in HARD mode`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One", maxConcurrentProjects = 2)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { projectMemberRepo.countActiveByUserIn(any(), ProjectMemberStatus.ACTIVE) } returns
            listOf(UserMemberCount(user1.id, 2L))

        val hardService = serviceWith(MatchingProperties(capacityMode = CapacityMode.HARD))
        val result = hardService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findCandidatesForProject penalizes user at capacity in SOFT mode`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One", maxConcurrentProjects = 2)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { projectMemberRepo.countActiveByUserIn(any(), ProjectMemberStatus.ACTIVE) } returns
            listOf(UserMemberCount(user1.id, 2L))

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].capacityLoad).isEqualTo(2)
        assertThat(result[0].capacityMax).isEqualTo(2)
        // SOFT penalty: rawScore * (1 - 0.15) = rawScore * 0.85
        assertThat(result[0].score).isLessThan(1.0)
    }

    @Test
    fun `findCandidatesForProject reports capacity load and max for each candidate`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One", maxConcurrentProjects = 5)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { projectMemberRepo.countActiveByUserIn(any(), ProjectMemberStatus.ACTIVE) } returns
            listOf(UserMemberCount(user1.id, 3L))

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].capacityLoad).isEqualTo(3)
        assertThat(result[0].capacityMax).isEqualTo(5)
    }

    // --- Phase 1: Asymmetric scoring ---

    @Test
    fun `findProjectsForUser computes growthPotential of 1 when all skills within level zone`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 4)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].growthPotential).isEqualTo(1.0)
    }

    @Test
    fun `findProjectsForUser computes growthPotential of 0 when skills far outside level zone`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 5)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every {
            projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE)
        } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].growthPotential).isEqualTo(0.0)
    }

    // --- Phase 2: Skill-Graph integration ---

    @Test
    fun `findCandidatesForProject matches user with related skill and applies transfer penalty`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "java@firma.de", firstName = "Java", lastName = "Dev")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val usJava = UserSkillBuilder().build(user = user1, skill = java, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(usJava)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { skillGraphService.expandSkills(any()) } returns
            mapOf(
                kotlin.id to
                    listOf(
                        SkillRelationInfo(
                            relatedSkill = java,
                            transferPenalty = 0.7,
                            relationType = SkillRelationType.SIMILAR_TO,
                            source = SkillRelationSource.CURATED,
                        ),
                    ),
            )

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        val match = result[0]
        assertThat(match.matchedSkills).hasSize(1)
        assertThat(match.matchedSkills[0].skillName).isEqualTo("kotlin")
        assertThat(match.matchedSkills[0].matchedVia).isEqualTo("java")
        // Must-have not directly fulfilled → STRETCH tier
        assertThat(match.matchTier).isEqualTo("STRETCH")
        assertThat(match.breakdown.mustHaveCoverage).isEqualTo(0.0)
        // Level fit reduced by penalty: ratio=4/3, capped at 1.0, * 0.7 = 0.7
        assertThat(match.breakdown.levelFitScore).isGreaterThan(0.0).isLessThan(1.0)
    }

    @Test
    fun `findCandidatesForProject gives partial nice-to-have credit for related skill match`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "vue@firma.de", firstName = "Vue", lastName = "Dev")
        val react = SkillBuilder().build(name = "react")
        val vue = SkillBuilder().build(name = "vue")
        val psReact = ProjectSkillBuilder().build(project = project, skill = react, level = 3, priority = SkillPriority.NICE_TO_HAVE)
        val usVue = UserSkillBuilder().build(user = user1, skill = vue, level = 3)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psReact)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(usVue)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { skillGraphService.expandSkills(any()) } returns
            mapOf(
                react.id to
                    listOf(
                        SkillRelationInfo(
                            relatedSkill = vue,
                            transferPenalty = 0.7,
                            relationType = SkillRelationType.SIMILAR_TO,
                            source = SkillRelationSource.CURATED,
                        ),
                    ),
            )

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        // Nice-to-have coverage = 0.7 (penalty) / 1 nice-to-have
        assertThat(result[0].breakdown.niceToHaveCoverage).isEqualTo(0.7)
        assertThat(result[0].matchedSkills[0].matchedVia).isEqualTo("vue")
    }

    @Test
    fun `findCandidatesForProject prefers direct match over related skill match`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "full@firma.de", firstName = "Full", lastName = "Stack")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val usKotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 3)
        val usJava = UserSkillBuilder().build(user = user1, skill = java, level = 3)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(usKotlin, usJava)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { skillGraphService.expandSkills(any()) } returns
            mapOf(
                kotlin.id to
                    listOf(
                        SkillRelationInfo(
                            relatedSkill = java,
                            transferPenalty = 0.7,
                            relationType = SkillRelationType.SIMILAR_TO,
                            source = SkillRelationSource.CURATED,
                        ),
                    ),
            )

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].matchedSkills).hasSize(1)
        assertThat(result[0].matchedSkills[0].matchedVia).isNull()
        assertThat(result[0].breakdown.mustHaveCoverage).isEqualTo(1.0)
        assertThat(result[0].matchTier).isEqualTo("EXACT")
    }

    @Test
    fun `findCandidatesForProject expands query to include related skills`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "java@firma.de", firstName = "Java", lastName = "Dev")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val usJava = UserSkillBuilder().build(user = user1, skill = java, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(usJava)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { skillGraphService.expandSkills(any()) } returns
            mapOf(
                kotlin.id to
                    listOf(
                        SkillRelationInfo(
                            relatedSkill = java,
                            transferPenalty = 0.7,
                            relationType = SkillRelationType.SIMILAR_TO,
                            source = SkillRelationSource.CURATED,
                        ),
                    ),
            )

        matchingService.findCandidatesForProject(project.id, 0.0, 20)

        // Verify the query was called with both kotlin and java (expanded)
        val slot = mutableListOf<Collection<SkillModel>>()
        io.mockk.verify { userSkillRepo.findMatchableBySkillsForProject(capture(slot), project, ProjectMemberStatus.ACTIVE) }
        val queriedSkillNames = slot.first().map { it.name }.toSet()
        assertThat(queriedSkillNames).contains("kotlin", "java")
    }

    // --- Phase 3: Application integration ---

    @Test
    fun `findCandidatesForProject sets hasApplied true when user has PENDING application`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)
        val application = ProjectApplicationBuilder().build(project = project, user = user1, status = ApplicationStatus.PENDING)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { applicationRepo.findByProjectAndUserInAndStatus(project, any(), ApplicationStatus.PENDING) } returns listOf(application)

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].hasApplied).isTrue()
    }

    @Test
    fun `findCandidatesForProject sets hasApplied false when user has no application`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 4)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].hasApplied).isFalse()
    }

    @Test
    fun `findCandidatesForProject applies score bonus when user has PENDING application`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val userWithApp = UserBuilder().build(email = "with-app@firma.de", firstName = "With", lastName = "App")
        val userWithoutApp = UserBuilder().build(email = "no-app@firma.de", firstName = "No", lastName = "App")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val docker = SkillBuilder().build(name = "docker")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE)
        val psDocker = ProjectSkillBuilder().build(project = project, skill = docker, level = 2, priority = SkillPriority.NICE_TO_HAVE)
        val usWithApp = UserSkillBuilder().build(user = userWithApp, skill = kotlin, level = 3)
        val usWithoutApp = UserSkillBuilder().build(user = userWithoutApp, skill = kotlin, level = 3)
        val application = ProjectApplicationBuilder().build(project = project, user = userWithApp, status = ApplicationStatus.PENDING)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin, psDocker)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns
            listOf(usWithApp, usWithoutApp)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()
        every { applicationRepo.findByProjectAndUserInAndStatus(project, any(), ApplicationStatus.PENDING) } returns listOf(application)

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(2)
        val withApp = result.first { it.userId == userWithApp.id }
        val withoutApp = result.first { it.userId == userWithoutApp.id }
        assertThat(withoutApp.score).isEqualTo(0.90)
        assertThat(withApp.score).isEqualTo(0.95)
    }

    @Test
    fun `findProjectsForUser sets applicationStatus when user has application`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 3)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 2, priority = SkillPriority.MUST_HAVE)
        val application = ProjectApplicationBuilder().build(project = project, user = user, status = ApplicationStatus.PENDING)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every { projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE) } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()
        every { applicationRepo.findByUserAndProjectIn(user, any()) } returns listOf(application)

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].applicationStatus).isEqualTo("PENDING")
    }

    @Test
    fun `findProjectsForUser sets applicationStatus null when user has no application`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 3)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 2, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every { projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE) } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        assertThat(result[0].applicationStatus).isNull()
    }

    // --- underutilizationPenalty (User-side only) ---

    @Test
    fun `findProjectsForUser penalizes overqualified user via underutilization factor`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 5)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every { projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE) } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        // rawScore = 0.25*1 + 0.15*1 + 0.10*1 + 0.10*1 + 0.40*0 = 0.60
        // underutilizationFactor: 5 > 1+2=3 → 1.0 - 0.2*1.0 = 0.8
        // score = 0.60 * 0.8 = 0.48
        assertThat(result[0].score).isEqualTo(0.48)
    }

    @Test
    fun `findProjectsForUser does not penalize user within threshold`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 3)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 2, priority = SkillPriority.MUST_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin)
        every { projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE) } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        // 3 > 2+2=4? No → factor = 1.0
        // rawScore = 0.25*1 + 0.15*1 + 0.10*1 + 0.10*1 + 0.40*1 (growth: abs(3-2)=1 ≤ 1) = 1.0
        // score = 1.0 * 1.0 = 1.0
        assertThat(result[0].score).isEqualTo(1.0)
    }

    @Test
    fun `findProjectsForUser applies partial penalty when some skills are underutilized`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val user = UserBuilder().build()
        val project = ProjectBuilder().build(owner = owner)
        val kotlin = SkillBuilder().build(name = "kotlin")
        val docker = SkillBuilder().build(name = "docker")
        val usKotlin = UserSkillBuilder().build(user = user, skill = kotlin, level = 5)
        val usDocker = UserSkillBuilder().build(user = user, skill = docker, level = 3)
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)
        val psDocker = ProjectSkillBuilder().build(project = project, skill = docker, level = 2, priority = SkillPriority.NICE_TO_HAVE)

        every { userSkillRepo.findByUser(user) } returns listOf(usKotlin, usDocker)
        every { projectRepo.findMatchableForUser(user, any(), ProjectMemberStatus.ACTIVE) } returns listOf(project)
        every { projectSkillRepo.findByProjectIn(any()) } returns listOf(psKotlin, psDocker)
        every { availabilityRepo.findByUser(user) } returns emptyList()

        val result = matchingService.findProjectsForUser(user, 0.0, 20)

        assertThat(result).hasSize(1)
        // rawScore = 0.25*1 + 0.15*1 + 0.10*1 + 0.10*1 + 0.40*0.5 (growth: only docker in zone) = 0.80
        // underutilization: kotlin 5>1+2=3 → yes; docker 3>2+2=4 → no. ratio = 1/2 = 0.5
        // factor = 1.0 - 0.2*0.5 = 0.9
        // score = 0.80 * 0.9 = 0.72
        assertThat(result[0].score).isEqualTo(0.72)
    }

    @Test
    fun `findCandidatesForProject does not apply underutilization penalty on PM-side`() {
        val owner = UserBuilder().build(email = "pm@firma.de", firstName = "PM", lastName = "User")
        val project = ProjectBuilder().build(owner = owner)
        val user1 = UserBuilder().build(email = "user1@firma.de", firstName = "User", lastName = "One")
        val kotlin = SkillBuilder().build(name = "kotlin")
        val psKotlin = ProjectSkillBuilder().build(project = project, skill = kotlin, level = 1, priority = SkillPriority.MUST_HAVE)
        val us1Kotlin = UserSkillBuilder().build(user = user1, skill = kotlin, level = 5)

        every { projectRepo.findById(project.id) } returns Optional.of(project)
        every { projectSkillRepo.findByProject(project) } returns listOf(psKotlin)
        every { userSkillRepo.findMatchableBySkillsForProject(any(), project, ProjectMemberStatus.ACTIVE) } returns listOf(us1Kotlin)
        every { availabilityRepo.findByUserIn(any()) } returns emptyList()

        val result = matchingService.findCandidatesForProject(project.id, 0.0, 20)

        assertThat(result).hasSize(1)
        // PM-side: no underutilization penalty. rawScore = 0.45*1 + 0.25*1 + 0.10*1 + 0.20*1 = 1.0
        // No capacity penalty, no application bonus. score = 1.0
        assertThat(result[0].score).isEqualTo(1.0)
    }
}
