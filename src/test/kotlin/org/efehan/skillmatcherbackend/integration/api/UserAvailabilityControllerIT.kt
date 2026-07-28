package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.requests.UserAvailabilityFixtures
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserAvailabilityModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDate

@DisplayName("UserAvailabilityController Integration Tests")
class UserAvailabilityControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should create availability and return 201`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request = UserAvailabilityFixtures.buildCreateAvailabilityRequest()

        // when & then
        mockMvc
            .post("/api/me/availability") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.availableFrom") { value(request.availableFrom.toString()) }
                jsonPath("$.availableTo") { value(request.availableTo.toString()) }
                jsonPath("$.id") { isNotEmpty() }
            }
    }

    @Test
    fun `should return 409 when availability periods overlap`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        userAvailabilityRepository.save(
            UserAvailabilityModel(
                user = user,
                availableFrom = LocalDate.of(2026, 3, 1),
                availableTo = LocalDate.of(2026, 6, 1),
            ),
        )
        val request =
            UserAvailabilityFixtures.buildCreateAvailabilityRequest(
                availableFrom = LocalDate.of(2026, 5, 1),
                availableTo = LocalDate.of(2026, 8, 1),
            )

        // when & then
        mockMvc
            .post("/api/me/availability") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("USER_AVAILABILITY_OVERLAP") }
            }
    }

    @Test
    fun `should return 400 when availability date range is invalid on create`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request =
            UserAvailabilityFixtures.buildCreateAvailabilityRequest(
                availableFrom = LocalDate.of(2026, 6, 1),
                availableTo = LocalDate.of(2026, 3, 1),
            )

        // when & then
        mockMvc
            .post("/api/me/availability") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return all availability entries sorted`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        userAvailabilityRepository.save(
            UserAvailabilityModel(
                user = user,
                availableFrom = LocalDate.of(2026, 6, 1),
                availableTo = LocalDate.of(2026, 9, 1),
            ),
        )
        userAvailabilityRepository.save(
            UserAvailabilityModel(
                user = user,
                availableFrom = LocalDate.of(2026, 1, 1),
                availableTo = LocalDate.of(2026, 3, 1),
            ),
        )

        // when & then
        mockMvc
            .get("/api/me/availability") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].availableFrom") { value("2026-01-01") }
                jsonPath("$[1].availableFrom") { value("2026-06-01") }
            }
    }

    @Test
    fun `should return empty list when no entries exist`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/me/availability") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `should update availability and return 200`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val entry =
            userAvailabilityRepository.save(
                UserAvailabilityModel(
                    user = user,
                    availableFrom = LocalDate.of(2026, 3, 1),
                    availableTo = LocalDate.of(2026, 6, 1),
                ),
            )
        val request = UserAvailabilityFixtures.buildUpdateAvailabilityRequest()

        // when & then
        mockMvc
            .put("/api/me/availability/${entry.id}") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.availableFrom") { value(request.availableFrom.toString()) }
                jsonPath("$.availableTo") { value(request.availableTo.toString()) }
            }
    }

    @Test
    fun `should return 404 when updating nonexistent entry`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request = UserAvailabilityFixtures.buildUpdateAvailabilityRequest()

        // when & then
        mockMvc
            .put("/api/me/availability/nonexistent-id") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("USER_AVAILABILITY_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when updating other users entry`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val otherUser =
            userRepository.save(
                UserModel(
                    email = "other@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Other",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val entry =
            userAvailabilityRepository.save(
                UserAvailabilityModel(
                    user = otherUser,
                    availableFrom = LocalDate.of(2026, 3, 1),
                    availableTo = LocalDate.of(2026, 6, 1),
                ),
            )
        val request = UserAvailabilityFixtures.buildUpdateAvailabilityRequest()

        // when & then
        mockMvc
            .put("/api/me/availability/${entry.id}") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("USER_AVAILABILITY_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should return 400 when availability date range is invalid on update`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val entry =
            userAvailabilityRepository.save(
                UserAvailabilityModel(
                    user = user,
                    availableFrom = LocalDate.of(2026, 3, 1),
                    availableTo = LocalDate.of(2026, 6, 1),
                ),
            )
        val request =
            UserAvailabilityFixtures.buildUpdateAvailabilityRequest(
                availableFrom = LocalDate.of(2026, 7, 1),
                availableTo = LocalDate.of(2026, 4, 1),
            )

        // when & then
        mockMvc
            .put("/api/me/availability/${entry.id}") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should delete availability and return 204`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val entry =
            userAvailabilityRepository.save(
                UserAvailabilityModel(
                    user = user,
                    availableFrom = LocalDate.of(2026, 3, 1),
                    availableTo = LocalDate.of(2026, 6, 1),
                ),
            )

        // when & then
        mockMvc
            .delete("/api/me/availability/${entry.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        mockMvc
            .get("/api/me/availability")
            .andExpect { status { isUnauthorized() } }

        mockMvc
            .post("/api/me/availability")
            .andExpect { status { isForbidden() } }
    }
}
