package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.efehan.skillmatcherbackend.config.WebSocketPrincipal
import org.efehan.skillmatcherbackend.config.WebSocketSessionRegistry
import org.efehan.skillmatcherbackend.config.WebSocketSessionRevalidator
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.fixtures.builder.RoleBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.simp.user.SimpUser
import org.springframework.messaging.simp.user.SimpUserRegistry

@ExtendWith(MockKExtension::class)
@DisplayName("WebSocketSessionRevalidator Unit Tests")
class WebSocketSessionRevalidatorTest {
    @MockK
    private lateinit var userRegistry: ObjectProvider<SimpUserRegistry>

    @MockK
    private lateinit var sessionRegistry: WebSocketSessionRegistry

    @MockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var revalidator: WebSocketSessionRevalidator

    @Test
    fun `keeps session when database still matches the snapshot`() {
        // given
        val user = UserBuilder().build()
        givenConnected(user)
        every { userRepository.findAllById(listOf(user.id)) } returns listOf(user)

        // when
        revalidator.revalidate()

        // then
        verify(exactly = 0) { sessionRegistry.disconnect(any()) }
    }

    @Test
    fun `disconnects when the user was disabled`() {
        // given
        val user = UserBuilder().build()
        givenConnected(user)
        every { userRepository.findAllById(listOf(user.id)) } returns listOf(copyOf(user, isEnabled = false))
        every { sessionRegistry.disconnect(user.id) } returns Unit

        // when
        revalidator.revalidate()

        // then
        verify(exactly = 1) { sessionRegistry.disconnect(user.id) }
    }

    @Test
    fun `disconnects when the role changed since CONNECT`() {
        // given
        val user = UserBuilder().build()
        givenConnected(user)
        every { userRepository.findAllById(listOf(user.id)) } returns listOf(copyOf(user, role = "ADMIN"))
        every { sessionRegistry.disconnect(user.id) } returns Unit

        // when
        revalidator.revalidate()

        // then
        verify(exactly = 1) { sessionRegistry.disconnect(user.id) }
    }

    @Test
    fun `disconnects when the user no longer exists`() {
        // given
        val user = UserBuilder().build()
        givenConnected(user)
        every { userRepository.findAllById(listOf(user.id)) } returns emptyList()
        every { sessionRegistry.disconnect(user.id) } returns Unit

        // when
        revalidator.revalidate()

        // then
        verify(exactly = 1) { sessionRegistry.disconnect(user.id) }
    }

    @Test
    fun `does not query the database without connected users`() {
        // given
        every { userRegistry.getObject() } returns mockk { every { users } returns emptySet() }

        // when
        revalidator.revalidate()

        // then
        verify(exactly = 0) { userRepository.findAllById(any()) }
    }

    private fun givenConnected(user: UserModel) {
        val simpUser =
            mockk<SimpUser> {
                every { name } returns user.id
                every { principal } returns WebSocketPrincipal(SecurityUser(user))
            }
        every { userRegistry.getObject() } returns mockk { every { users } returns setOf(simpUser) }
    }

    private fun copyOf(
        user: UserModel,
        isEnabled: Boolean = true,
        role: String = user.role.name,
    ): UserModel =
        UserBuilder().build(email = user.email, role = RoleBuilder().build(name = role), isEnabled = isEnabled).also {
            it.id = user.id
        }
}
