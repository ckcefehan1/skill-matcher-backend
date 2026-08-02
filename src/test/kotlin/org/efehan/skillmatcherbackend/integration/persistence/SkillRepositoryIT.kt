package org.efehan.skillmatcherbackend.integration.persistence

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("SkillRepository Integration Tests")
class SkillRepositoryIT : AbstractIntegrationTest() {
    @Test
    fun `findOrCreate normalizes the name and reuses the existing skill`() {
        // when
        val created = skillRepository.findOrCreate("  Kotlin  ")
        val reused = skillRepository.findOrCreate("KOTLIN")

        // then
        assertThat(created.name).isEqualTo("kotlin")
        assertThat(reused.id).isEqualTo(created.id)
        assertThat(skillRepository.findAll()).hasSize(1)
    }

    @Test
    fun `insertIfAbsent keeps the first row when the same name is inserted twice`() {
        // given
        val first = skillRepository.findOrCreate("kotlin")

        // when
        skillRepository.insertIfAbsent(UUID.randomUUID().toString(), "kotlin")

        // then
        val all = skillRepository.findAll()
        assertThat(all).hasSize(1)
        assertThat(all.first().id).isEqualTo(first.id)
    }
}
