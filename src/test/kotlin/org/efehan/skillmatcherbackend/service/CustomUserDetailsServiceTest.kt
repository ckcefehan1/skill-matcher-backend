package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.auth.CustomUserDetailsService
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.core.userdetails.UsernameNotFoundException

@ExtendWith(MockKExtension::class)
@DisplayName("CustomUserDetailsService Unit Tests")
class CustomUserDetailsServiceTest {
    @MockK
    private lateinit var userRepository: UserRepository

    private lateinit var service: CustomUserDetailsService

    companion object {
        private const val EMAIL = "test@example.com"
    }

    @BeforeEach
    fun setUp() {
        service = CustomUserDetailsService(UserService(userRepository))
    }

    private fun buildTestUser(
        email: String = EMAIL,
        isEnabled: Boolean = true,
        roleName: String = "USER",
        passwordHash: String? = "hashedPassword",
    ): UserModel {
        val role = RoleModel(name = roleName, description = null)
        val user =
            UserModel(
                email = email,
                passwordHash = passwordHash,
                firstName = "Test",
                lastName = "User",
                role = role,
            )
        user.isEnabled = isEnabled
        return user
    }

    @Test
    fun `loadUserByUsername returns SecurityUser when user exists`() {
        // given
        val user = buildTestUser()
        every { userRepository.findByEmail(EMAIL) } returns user

        // when
        val result = service.loadUserByUsername(EMAIL)

        // then
        assertThat(result).isInstanceOf(SecurityUser::class.java)
        assertThat(result.username).isEqualTo(EMAIL)
        assertThat(result.password).isEqualTo("hashedPassword")
        assertThat(result.isEnabled).isTrue()
    }

    @Test
    fun `loadUserByUsername returns correct authorities based on user role`() {
        // given
        val user = buildTestUser(roleName = "ADMIN")
        every { userRepository.findByEmail(EMAIL) } returns user

        // when
        val result = service.loadUserByUsername(EMAIL)

        // then
        assertThat(result.authorities).hasSize(1)
        assertThat(result.authorities.first().authority).isEqualTo("ROLE_ADMIN")
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when user not found`() {
        // given
        every { userRepository.findByEmail(EMAIL) } returns null

        // then
        assertThatThrownBy { service.loadUserByUsername(EMAIL) }
            .isInstanceOf(UsernameNotFoundException::class.java)
            .hasMessage("User not found")
    }

    @Test
    fun `loadUserByUsername returns disabled SecurityUser when user is disabled`() {
        // given
        val user = buildTestUser(isEnabled = false)
        every { userRepository.findByEmail(EMAIL) } returns user

        // when
        val result = service.loadUserByUsername(EMAIL)

        // then
        assertThat(result.isEnabled).isFalse()
    }

    @Test
    fun `loadUserByUsername returns empty password when passwordHash is null`() {
        // given
        val user = buildTestUser(passwordHash = null)
        every { userRepository.findByEmail(EMAIL) } returns user

        // when
        val result = service.loadUserByUsername(EMAIL)

        // then
        assertThat(result.password).isEmpty()
    }
}
