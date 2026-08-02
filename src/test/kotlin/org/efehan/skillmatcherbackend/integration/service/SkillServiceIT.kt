package org.efehan.skillmatcherbackend.integration.service

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.skill.SkillService
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

@DisplayName("SkillService Integration Tests")
class SkillServiceIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var skillService: SkillService

    @Test
    fun `findOrCreate normalizes the name and reuses the existing skill`() {
        // when
        val created = skillService.findOrCreate("  Kotlin  ")
        val reused = skillService.findOrCreate("KOTLIN")

        // then
        assertThat(created.name).isEqualTo("kotlin")
        assertThat(reused.id).isEqualTo(created.id)
        assertThat(skillRepository.findAll()).hasSize(1)
    }

    @Test
    fun `insertIfAbsent keeps the first row when the same name is inserted twice`() {
        // given
        val first = skillService.findOrCreate("kotlin")

        // when
        skillRepository.insertIfAbsent(UUID.randomUUID().toString(), "kotlin")

        // then
        val all = skillRepository.findAll()
        assertThat(all).hasSize(1)
        assertThat(all.first().id).isEqualTo(first.id)
    }
}
