package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.core.matching.MatchTier
import org.efehan.skillmatcherbackend.core.matching.MatchingService
import org.efehan.skillmatcherbackend.core.skill.SkillService
import org.efehan.skillmatcherbackend.core.skill.UserSkillService
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectSkillModel
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserSkillModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.LocalDate

@DisplayName("Caching Integration Tests")
class CachingIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var userSkillService: UserSkillService

    @Autowired
    private lateinit var skillService: SkillService

    @Autowired
    private lateinit var matchingService: MatchingService

    @Test
    fun `skill catalog is cached and evicted when a skill is added`() {
        val user = saveUser("max@firma.de")
        skillRepository.save(SkillModel(name = "kotlin"))

        skillService.getAllSkills(PageRequest.of(0, 20))
        assertThat(caffeineMap(CacheConfig.SKILL_CATALOG)).isNotEmpty

        userSkillService.addOrUpdateSkill(user, "java", 3)
        assertThat(caffeineMap(CacheConfig.SKILL_CATALOG)).isEmpty()
    }

    @Test
    fun `matching result is cached and evicted when user skills change`() {
        val pm = saveUser("pm@firma.de")
        val user = saveUser("dev@firma.de")
        val kotlin = skillRepository.save(SkillModel(name = "kotlin"))
        userSkillRepository.save(UserSkillModel(user = user, skill = kotlin, level = 4))

        val project =
            projectRepository.save(
                ProjectModel(
                    name = "Test Project",
                    description = "Test Description",
                    status = ProjectStatus.PLANNED,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 9, 1),
                    maxMembers = 5,
                    owner = pm,
                ),
            )
        projectSkillRepository.save(
            ProjectSkillModel(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE),
        )

        val first = matchingService.findProjectsForUser(user, 0.0, 20, MatchTier.ALL)
        assertThat(first).isNotEmpty
        assertThat(caffeineMap(CacheConfig.MATCHING_PROJECTS_FOR_USER)).isNotEmpty

        userSkillService.addOrUpdateSkill(user, "java", 3)
        assertThat(caffeineMap(CacheConfig.MATCHING_PROJECTS_FOR_USER)).isEmpty()
    }

    private fun saveUser(email: String): UserModel {
        val role = roleRepository.findByName("EMPLOYER") ?: roleRepository.save(RoleModel("EMPLOYER", null))
        return userRepository.save(
            UserModel(
                email = email,
                passwordHash = "hashed",
                firstName = "Max",
                lastName = "Mustermann",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    private fun caffeineMap(cacheName: String): Map<*, *> {
        val cache = cacheManager.getCache(cacheName)!!
        val nativeCache = cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>
        return nativeCache.asMap()
    }
}
