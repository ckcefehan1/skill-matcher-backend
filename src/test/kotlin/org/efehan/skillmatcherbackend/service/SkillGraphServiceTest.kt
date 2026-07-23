package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.properties.SkillGraphProperties
import org.efehan.skillmatcherbackend.core.skill.SkillGraphService
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.SkillRelationBuilder
import org.efehan.skillmatcherbackend.persistence.SkillCoOccurrence
import org.efehan.skillmatcherbackend.persistence.SkillRelationModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationRepository
import org.efehan.skillmatcherbackend.persistence.SkillRelationSource
import org.efehan.skillmatcherbackend.persistence.SkillRelationType
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("SkillGraphService Unit Tests")
class SkillGraphServiceTest {
    @MockK
    private lateinit var skillRelationRepo: SkillRelationRepository

    @MockK
    private lateinit var userSkillRepo: UserSkillRepository

    private val properties = SkillGraphProperties()

    @InjectMockKs
    private lateinit var service: SkillGraphService

    @Test
    fun `expandSkills returns empty map for empty input`() {
        val result = service.expandSkills(emptyList())

        assertThat(result).isEmpty()
        verify(exactly = 0) { skillRelationRepo.findBySkillIn(any()) }
    }

    @Test
    fun `expandSkills returns related skills bidirectionally for each input skill`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val relation =
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                transferPenalty = 0.7,
            )
        every { skillRelationRepo.findBySkillIn(listOf(kotlin)) } returns listOf(relation)

        val result = service.expandSkills(listOf(kotlin))

        assertThat(result).hasSize(1)
        assertThat(result[kotlin.id]!!).hasSize(1)
        assertThat(result[kotlin.id]!![0].relatedSkill.id).isEqualTo(java.id)
        assertThat(result[kotlin.id]!![0].transferPenalty).isEqualTo(0.7)
    }

    @Test
    fun `expandSkills returns reverse direction when only toSkill is in input`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val relation =
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                transferPenalty = 0.7,
            )
        every { skillRelationRepo.findBySkillIn(listOf(java)) } returns listOf(relation)

        val result = service.expandSkills(listOf(java))

        assertThat(result).hasSize(1)
        assertThat(result[java.id]!!).hasSize(1)
        assertThat(result[java.id]!![0].relatedSkill.id).isEqualTo(kotlin.id)
    }

    @Test
    fun `expandSkills filters out relations below maxTransferPenalty`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val python = SkillBuilder().build(name = "python")
        val strongRelation =
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                transferPenalty = 0.8,
            )
        val weakRelation =
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = python,
                transferPenalty = 0.3,
            )
        every { skillRelationRepo.findBySkillIn(listOf(kotlin)) } returns listOf(strongRelation, weakRelation)

        val result = service.expandSkills(listOf(kotlin))

        assertThat(result[kotlin.id]!!).hasSize(1)
        assertThat(result[kotlin.id]!![0].relatedSkill.id).isEqualTo(java.id)
    }

    @Test
    fun `deriveCoOccurrence does nothing when derivation disabled`() {
        val disabledService =
            SkillGraphService(
                skillRelationRepo,
                userSkillRepo,
                SkillGraphProperties(derivationEnabled = false),
            )

        disabledService.deriveCoOccurrence()

        verify(exactly = 0) { userSkillRepo.findSkillCoOccurrence(any()) }
    }

    @Test
    fun `deriveCoOccurrence does nothing when no co-occurrences found`() {
        every { userSkillRepo.findSkillCoOccurrence(5L) } returns emptyList()

        service.deriveCoOccurrence()

        verify(exactly = 0) { skillRelationRepo.save(any()) }
    }

    @Test
    fun `deriveCoOccurrence inserts LEARNED relation for new co-occurring pair`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val co = SkillCoOccurrence(fromSkill = kotlin, toSkill = java, count = 10L)
        every { userSkillRepo.findSkillCoOccurrence(5L) } returns listOf(co)
        every { skillRelationRepo.findBySkillIn(any()) } returns emptyList()
        every { skillRelationRepo.findByFromSkillAndToSkill(kotlin, java) } returns null
        every { skillRelationRepo.save(any()) } returnsArgument 0

        service.deriveCoOccurrence()

        val saved = slot<SkillRelationModel>()
        verify(exactly = 1) { skillRelationRepo.save(capture(saved)) }
        assertThat(saved.captured.fromSkill.id).isEqualTo(kotlin.id)
        assertThat(saved.captured.toSkill.id).isEqualTo(java.id)
        assertThat(saved.captured.source).isEqualTo(SkillRelationSource.LEARNED)
        assertThat(saved.captured.relationType).isEqualTo(SkillRelationType.SIMILAR_TO)
        // count=10, maxCount=10, normalized=1.0 → 0.3 + 0.4*1.0 = 0.7
        assertThat(saved.captured.transferPenalty).isEqualTo(0.7)
    }

    @Test
    fun `deriveCoOccurrence updates existing LEARNED relation with new penalty`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val co = SkillCoOccurrence(fromSkill = kotlin, toSkill = java, count = 10L)
        val existing =
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                transferPenalty = 0.5,
                source = SkillRelationSource.LEARNED,
            )
        every { userSkillRepo.findSkillCoOccurrence(5L) } returns listOf(co)
        every { skillRelationRepo.findBySkillIn(any()) } returns emptyList()
        every { skillRelationRepo.findByFromSkillAndToSkill(kotlin, java) } returns existing
        every { skillRelationRepo.save(any()) } returnsArgument 0

        service.deriveCoOccurrence()

        verify(exactly = 0) { skillRelationRepo.save(any()) }
        assertThat(existing.transferPenalty).isEqualTo(0.7)
    }

    @Test
    fun `deriveCoOccurrence skips pairs that already have CURATED relation`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val co = SkillCoOccurrence(fromSkill = kotlin, toSkill = java, count = 10L)
        val curated =
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                transferPenalty = 0.9,
                source = SkillRelationSource.CURATED,
            )
        every { userSkillRepo.findSkillCoOccurrence(5L) } returns listOf(co)
        every { skillRelationRepo.findBySkillIn(any()) } returns listOf(curated)

        service.deriveCoOccurrence()

        verify(exactly = 0) { skillRelationRepo.findByFromSkillAndToSkill(any(), any()) }
        verify(exactly = 0) { skillRelationRepo.save(any()) }
    }

    @Test
    fun `deriveCoOccurrence computes lower penalty for weaker co-occurrence`() {
        val kotlin = SkillBuilder().build(name = "kotlin")
        val java = SkillBuilder().build(name = "java")
        val python = SkillBuilder().build(name = "python")
        // Strong pair (count = maxCount = 100) and weak pair (count = 5)
        val strongCo = SkillCoOccurrence(fromSkill = kotlin, toSkill = java, count = 100L)
        val weakCo = SkillCoOccurrence(fromSkill = kotlin, toSkill = python, count = 5L)
        every { userSkillRepo.findSkillCoOccurrence(5L) } returns listOf(strongCo, weakCo)
        every { skillRelationRepo.findBySkillIn(any()) } returns emptyList()
        every { skillRelationRepo.findByFromSkillAndToSkill(any(), any()) } returns null
        every { skillRelationRepo.save(any()) } returnsArgument 0

        service.deriveCoOccurrence()

        val saved = mutableListOf<SkillRelationModel>()
        verify(exactly = 2) { skillRelationRepo.save(capture(saved)) }
        val strong = saved.first { it.toSkill.id == java.id }
        val weak = saved.first { it.toSkill.id == python.id }
        // strong: 0.3 + 0.4 * (100/100) = 0.7
        assertThat(strong.transferPenalty).isEqualTo(0.7)
        // weak: 0.3 + 0.4 * (5/100) = 0.3 + 0.02 = 0.32 → rounded to 0.32
        assertThat(weak.transferPenalty).isEqualTo(0.32)
    }
}
