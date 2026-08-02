package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.skill.UserSkillService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserSkillBuilder
import org.efehan.skillmatcherbackend.persistence.SkillRepository
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
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
@DisplayName("UserSkillService Unit Tests")
class UserSkillServiceTest {
    @MockK
    private lateinit var skillRepo: SkillRepository

    @MockK
    private lateinit var userSkillRepo: UserSkillRepository

    @InjectMockKs
    private lateinit var userSkillService: UserSkillService

    @Test
    fun `addOrUpdateSkill creates new skill and user skill`() {
        // given
        val user = UserBuilder().build()
        val skill = SkillBuilder().build(name = "kotlin")
        every { skillRepo.findOrCreate("Kotlin") } returns skill
        every { userSkillRepo.findByUserAndSkillId(user, skill.id) } returns null
        every { userSkillRepo.save(any()) } returnsArgument 0

        // when
        val (result, created) = userSkillService.addOrUpdateSkill(user, "Kotlin", 3)

        // then
        assertThat(created).isTrue()
        assertThat(result.skill.name).isEqualTo("kotlin")
        assertThat(result.level).isEqualTo(3)
        verify(exactly = 1) { userSkillRepo.save(any()) }
    }

    @Test
    fun `addOrUpdateSkill updates level when user already has the skill`() {
        // given
        val user = UserBuilder().build()
        val skill = SkillBuilder().build(name = "kotlin")
        val existingUserSkill = UserSkillBuilder().build(user = user, skill = skill, level = 2)
        every { skillRepo.findOrCreate("Kotlin") } returns skill
        every { userSkillRepo.findByUserAndSkillId(user, skill.id) } returns existingUserSkill
        every { userSkillRepo.save(any()) } returnsArgument 0

        // when
        val (result, created) = userSkillService.addOrUpdateSkill(user, "Kotlin", 5)

        // then
        assertThat(created).isFalse()
        assertThat(result.level).isEqualTo(5)
    }

    @Test
    fun `addOrUpdateSkill throws when level is below 1`() {
        // given
        val user = UserBuilder().build()

        // then
        assertThatThrownBy {
            userSkillService.addOrUpdateSkill(user, "Kotlin", 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(exactly = 0) { userSkillRepo.save(any()) }
    }

    @Test
    fun `addOrUpdateSkill throws when level is above 5`() {
        // given
        val user = UserBuilder().build()

        // then
        assertThatThrownBy {
            userSkillService.addOrUpdateSkill(user, "Kotlin", 6)
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(exactly = 0) { userSkillRepo.save(any()) }
    }

    @Test
    fun `getAllSkills returns all skills`() {
        // given
        val skills =
            listOf(
                SkillBuilder().build(name = "kotlin"),
                SkillBuilder().build(name = "java"),
                SkillBuilder().build(name = "spring"),
            )
        every { skillRepo.findAll(any<Pageable>()) } returns PageImpl(skills)

        // when
        val result = userSkillService.getAllSkills(PageRequest.of(0, 20))

        // then
        assertThat(result.content).hasSize(3)
        assertThat(result.content[0].name).isEqualTo("kotlin")
        assertThat(result.content[1].name).isEqualTo("java")
        assertThat(result.content[2].name).isEqualTo("spring")
    }

    @Test
    fun `getAllSkills returns empty list when no skills exist`() {
        // given
        every { skillRepo.findAll(any<Pageable>()) } returns PageImpl(emptyList())

        // when
        val result = userSkillService.getAllSkills(PageRequest.of(0, 20))

        // then
        assertThat(result.content).isEmpty()
    }

    @Test
    fun `getUserSkills returns user skills`() {
        // given
        val user = UserBuilder().build()
        val skill1 = SkillBuilder().build(name = "kotlin")
        val skill2 = SkillBuilder().build(name = "java")
        val userSkills =
            listOf(
                UserSkillBuilder().build(user = user, skill = skill1, level = 4),
                UserSkillBuilder().build(user = user, skill = skill2, level = 3),
            )
        every { userSkillRepo.findByUser(user) } returns userSkills

        // when
        val result = userSkillService.getUserSkills(user)

        // then
        assertThat(result).hasSize(2)
        assertThat(result[0].skill.name).isEqualTo("kotlin")
        assertThat(result[0].level).isEqualTo(4)
        assertThat(result[1].skill.name).isEqualTo("java")
        assertThat(result[1].level).isEqualTo(3)
    }

    @Test
    fun `getUserSkills returns empty list when user has no skills`() {
        // given
        val user = UserBuilder().build()
        every { userSkillRepo.findByUser(user) } returns emptyList()

        // when
        val result = userSkillService.getUserSkills(user)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `deleteSkill deletes user skill successfully`() {
        // given
        val user = UserBuilder().build()
        val userSkill = UserSkillBuilder().build(user = user)
        every { userSkillRepo.findById(userSkill.id) } returns Optional.of(userSkill)
        every { userSkillRepo.delete(userSkill) } returns Unit

        // when
        userSkillService.deleteSkill(user, userSkill.id)

        // then
        verify(exactly = 1) { userSkillRepo.delete(userSkill) }
    }

    @Test
    fun `deleteSkill throws EntryNotFoundException when skill not found`() {
        // given
        val user = UserBuilder().build()
        every { userSkillRepo.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            userSkillService.deleteSkill(user, "nonexistent")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.USER_SKILL_NOT_FOUND)
            })

        verify(exactly = 0) { userSkillRepo.delete(any()) }
    }

    @Test
    fun `deleteSkill throws AccessDeniedException when user tries to delete another users skill`() {
        // given
        val user = UserBuilder().build()
        val otherUser = UserBuilder().build(email = "other@firma.de", firstName = "Other", lastName = "User")
        val otherUsersSkill = UserSkillBuilder().build(user = otherUser)
        every { userSkillRepo.findById(otherUsersSkill.id) } returns Optional.of(otherUsersSkill)

        // then
        assertThatThrownBy {
            userSkillService.deleteSkill(user, otherUsersSkill.id)
        }.isInstanceOf(AccessDeniedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccessDeniedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.USER_SKILL_ACCESS_DENIED)
            })

        verify(exactly = 0) { userSkillRepo.delete(any()) }
    }
}
