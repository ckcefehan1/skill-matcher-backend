package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.core.matching.MatchTier
import org.efehan.skillmatcherbackend.core.matching.MatchingService
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ProjectSkillModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("Tenant-aware Cache Integration Tests")
class CacheTenantIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var matchingService: MatchingService

    @Test
    fun `matching cache keys contain the tenant`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val pm = userRepository.save(UserBuilder().build(email = "pm@firma-a.de", role = role))
        val skill = skillRepository.save(SkillModel(name = "kotlin"))
        val project = projectRepository.save(ProjectBuilder().build(owner = pm))
        projectSkillRepository.save(
            ProjectSkillModel(project = project, skill = skill, level = 3, priority = SkillPriority.MUST_HAVE),
        )

        // when
        matchingService.findCandidatesForProject(project.id, 0.0, 20, MatchTier.ALL)

        // then: cache key leads with the company id, so tenants can never share entries
        val cache = cacheManager.getCache(CacheConfig.MATCHING_CANDIDATES)!!
        val keys = (cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).asMap().keys
        assertThat(keys).hasSize(1)
        assertThat(keys.single().toString()).contains(companyA.id)
    }

    @Test
    fun `same lookup in another tenant is a cache miss`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val pm = userRepository.save(UserBuilder().build(email = "pm@firma-a.de", role = role))
        val skill = skillRepository.save(SkillModel(name = "kotlin"))
        val project = projectRepository.save(ProjectBuilder().build(owner = pm))
        projectSkillRepository.save(
            ProjectSkillModel(project = project, skill = skill, level = 3, priority = SkillPriority.MUST_HAVE),
        )
        matchingService.findCandidatesForProject(project.id, 0.0, 20, MatchTier.ALL)

        // when: tenant B looks up its own project with a same-shaped key
        TenantContext.set(companyB.id)
        val pmB = userRepository.save(UserBuilder().build(email = "pm@firma-b.de", role = role))
        val projectB = projectRepository.save(ProjectBuilder().build(owner = pmB, name = "B Project"))
        projectSkillRepository.save(
            ProjectSkillModel(project = projectB, skill = skill, level = 3, priority = SkillPriority.MUST_HAVE),
        )
        matchingService.findCandidatesForProject(projectB.id, 0.0, 20, MatchTier.ALL)

        // then: two entries, one per tenant
        val cache = cacheManager.getCache(CacheConfig.MATCHING_CANDIDATES)!!
        val keys = (cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).asMap().keys
        assertThat(keys).hasSize(2)
        assertThat(keys.map { it.toString() }.joinToString())
            .contains(companyA.id)
            .contains(companyB.id)
    }
}
