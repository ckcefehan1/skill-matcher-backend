package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.admin.AdminUserService
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.RoleBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.DuplicateEntryException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("Admin User Service Unit Tests")
class AdminUserServiceTest {
    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var roleRepository: RoleRepository

    @MockK
    private lateinit var invitationService: InvitationService

    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMockKs
    private lateinit var adminUserService: AdminUserService

    @Test
    fun `createUser successfully creates user and sends invitation`() {
        // given
        val role = RoleBuilder().build(name = "EMPLOYER")
        val userSlot = slot<UserModel>()

        every { userRepository.existsByEmail("max.mustermann@firma.de") } returns false
        every { roleRepository.findByName("EMPLOYER") } returns role
        every { userRepository.save(capture(userSlot)) } returnsArgument 0
        every { invitationService.createAndSendInvitation(any()) } just runs

        // when
        val result = adminUserService.createUser("max.mustermann@firma.de", "EMPLOYER")

        // then
        assertThat(result.email).isEqualTo("max.mustermann@firma.de")
        assertThat(result.role).isEqualTo(role)

        val savedUser = userSlot.captured
        assertThat(savedUser.isEnabled).isFalse()
        assertThat(savedUser.passwordHash).isNull()
        assertThat(savedUser.firstName).isNull()
        assertThat(savedUser.lastName).isNull()

        verify(exactly = 1) { invitationService.createAndSendInvitation(any()) }
    }

    @Test
    fun `createUser throws DuplicateEntryException when email already exists`() {
        // given
        every { userRepository.existsByEmail("max.mustermann@firma.de") } returns true

        // then
        assertThatThrownBy {
            adminUserService.createUser("max.mustermann@firma.de", "EMPLOYER")
        }.isInstanceOf(DuplicateEntryException::class.java)
            .satisfies({ ex ->
                val e = ex as DuplicateEntryException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.USER_ALREADY_EXISTS)
            })

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `createUser throws EntryNotFoundException when role does not exist`() {
        // given
        every { userRepository.existsByEmail("max.mustermann@firma.de") } returns false
        every { roleRepository.findByName("INVALID_ROLE") } returns null

        // then
        assertThatThrownBy {
            adminUserService.createUser("max.mustermann@firma.de", "INVALID_ROLE")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.ROLE_NOT_FOUND)
            })

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `createUser converts role to uppercase`() {
        // given
        val role = RoleBuilder().build(name = "EMPLOYER")

        every { userRepository.existsByEmail("max.mustermann@firma.de") } returns false
        every { roleRepository.findByName("EMPLOYER") } returns role
        every { userRepository.save(any()) } returnsArgument 0
        every { invitationService.createAndSendInvitation(any()) } just runs

        // when
        val result = adminUserService.createUser("max.mustermann@firma.de", "employer")

        // then
        assertThat(result.role.name).isEqualTo("EMPLOYER")
        verify { roleRepository.findByName("EMPLOYER") }
    }

    @Test
    fun `updateUserStatus disables user and revokes refresh tokens`() {
        // given
        val user = UserBuilder().build(email = "max@firma.de", role = RoleBuilder().build(name = "EMPLOYER"))

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.revokeAllUserTokens(user.id) } returns 2

        // when
        adminUserService.updateUserStatus(user.id, false)

        // then
        assertThat(user.isEnabled).isFalse()
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 1) { refreshTokenRepository.revokeAllUserTokens(user.id) }
    }

    @Test
    fun `updateUserStatus enables user without revoking refresh tokens`() {
        // given
        val user = UserBuilder().build(email = "max@firma.de", isEnabled = false, role = RoleBuilder().build(name = "EMPLOYER"))

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userRepository.save(any()) } returnsArgument 0

        // when
        adminUserService.updateUserStatus(user.id, true)

        // then
        assertThat(user.isEnabled).isTrue()
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 0) { refreshTokenRepository.revokeAllUserTokens(any()) }
    }

    @Test
    fun `updateUserStatus throws EntryNotFoundException when user not found`() {
        // given
        every { userRepository.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            adminUserService.updateUserStatus("nonexistent", false)
        }.isInstanceOf(EntryNotFoundException::class.java)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `listUsers returns all users`() {
        // given
        val user1 = UserBuilder().build(email = "max@firma.de", role = RoleBuilder().build(name = "EMPLOYER"))
        val user2 = UserBuilder().build(email = "admin@firma.de", isEnabled = false, role = RoleBuilder().build(name = "ADMIN"))

        every { userRepository.findAll(any<Pageable>()) } returns PageImpl(listOf(user1, user2))

        // when
        val result = adminUserService.listUsers(PageRequest.of(0, 20))

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].email).isEqualTo("max@firma.de")
        assertThat(result.content[0].role.name).isEqualTo("EMPLOYER")
        assertThat(result.content[0].isEnabled).isTrue()
        assertThat(result.content[1].email).isEqualTo("admin@firma.de")
        assertThat(result.content[1].role.name).isEqualTo("ADMIN")
        assertThat(result.content[1].isEnabled).isFalse()
    }

    @Test
    fun `listUsers returns empty list when no users exist`() {
        // given
        every { userRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())

        // when
        val result = adminUserService.listUsers(PageRequest.of(0, 20))

        // then
        assertThat(result.content).isEmpty()
    }

    @Test
    fun `updateUserRole updates role and revokes refresh tokens`() {
        // given
        val user = UserBuilder().build(email = "max@firma.de", role = RoleBuilder().build(name = "EMPLOYER"))
        val newRole = RoleBuilder().build(name = "ADMIN")

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roleRepository.findByName("ADMIN") } returns newRole
        every { userRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.revokeAllUserTokens(user.id) } returns 1

        // when
        adminUserService.updateUserRole(user.id, "ADMIN")

        // then
        assertThat(user.role).isEqualTo(newRole)
        verify(exactly = 1) { userRepository.save(user) }
        verify(exactly = 1) { refreshTokenRepository.revokeAllUserTokens(user.id) }
    }

    @Test
    fun `updateUserRole converts role name to uppercase`() {
        // given
        val user = UserBuilder().build(email = "max@firma.de", role = RoleBuilder().build(name = "EMPLOYER"))
        val newRole = RoleBuilder().build(name = "ADMIN")

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roleRepository.findByName("ADMIN") } returns newRole
        every { userRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.revokeAllUserTokens(user.id) } returns 1

        // when
        adminUserService.updateUserRole(user.id, "admin")

        // then
        verify { roleRepository.findByName("ADMIN") }
    }

    @Test
    fun `updateUserRole throws EntryNotFoundException when user not found`() {
        // given
        every { userRepository.findById("nonexistent") } returns Optional.empty()

        // then
        assertThatThrownBy {
            adminUserService.updateUserRole("nonexistent", "ADMIN")
        }.isInstanceOf(EntryNotFoundException::class.java)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `updateUserRole throws EntryNotFoundException when role not found`() {
        // given
        val user = UserBuilder().build(email = "max@firma.de", role = RoleBuilder().build(name = "EMPLOYER"))

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roleRepository.findByName("NONEXISTENT") } returns null

        // then
        assertThatThrownBy {
            adminUserService.updateUserRole(user.id, "NONEXISTENT")
        }.isInstanceOf(EntryNotFoundException::class.java)
            .satisfies({ ex ->
                val e = ex as EntryNotFoundException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.ROLE_NOT_FOUND)
            })

        verify(exactly = 0) { userRepository.save(any()) }
    }
}
