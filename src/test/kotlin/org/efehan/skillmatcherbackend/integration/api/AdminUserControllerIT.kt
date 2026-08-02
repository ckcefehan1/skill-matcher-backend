package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.requests.AdminUserFixtures
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@DisplayName("Admin User Controller Integration Tests")
class AdminUserControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should create user successfully as admin`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        roleRepository.save(RoleModel("EMPLOYER", null))
        val request = AdminUserFixtures.buildCreateUserRequest()

        // when & then
        mockMvc
            .post("/api/admin/users") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("max.mustermann@firma.de") }
                jsonPath("$.role") { value("EMPLOYER") }
                jsonPath("$.id") { isNotEmpty() }
            }
    }

    @Test
    fun `should return 409 when email already exists`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildCreateUserRequest(email = "admin@firma.de", role = "ADMIN")

        // when & then
        mockMvc
            .post("/api/admin/users") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("USER_ALREADY_EXISTS") }
            }
    }

    @Test
    fun `should return 403 when admin tries to invite a SUPERADMIN`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        roleRepository.save(RoleModel("SUPERADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildCreateUserRequest(role = "SUPERADMIN")

        // when & then
        mockMvc
            .post("/api/admin/users") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 403 when admin tries to assign SUPERADMIN`() {
        // given: the privilege escalation from the review — must stay closed
        val role = roleRepository.save(RoleModel("ADMIN", null))
        roleRepository.save(RoleModel("SUPERADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildUpdateUserRoleRequest(role = "SUPERADMIN")

        // when & then: even on their own user id
        mockMvc
            .patch("/api/admin/users/${admin.id}/role") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 400 when request body is invalid`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildCreateUserRequest(email = "not-an-email", role = "")

        // when & then
        mockMvc
            .post("/api/admin/users") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 403 when non-admin tries to create user`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "employer@firma.de",
                    passwordHash = passwordEncoder.encode("User-Password1!"),
                    firstName = "Normal",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request = AdminUserFixtures.buildCreateUserRequest()

        // when & then
        mockMvc
            .post("/api/admin/users") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 401 when not authenticated for create user`() {
        // when & then
        mockMvc
            .post("/api/admin/users") {
                withBodyRequest(AdminUserFixtures.buildCreateUserRequest())
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should return all users`() {
        // given
        val adminRole = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = adminRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val employerRole = roleRepository.save(RoleModel("EMPLOYER", null))
        userRepository.save(
            UserModel(
                email = "max@firma.de",
                passwordHash = passwordEncoder.encode("Test-Password1!"),
                firstName = "Max",
                lastName = "Mustermann",
                role = employerRole,
            ).apply { isEnabled = true },
        )

        // when & then
        mockMvc
            .get("/api/admin/users") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].id") { isNotEmpty() }
                jsonPath("$.content[0].email") { isNotEmpty() }
                jsonPath("$.content[0].role") { isNotEmpty() }
            }
    }

    @Test
    fun `should return only admin when no other users exist`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)

        // when & then
        mockMvc
            .get("/api/admin/users") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
            }
    }

    @Test
    fun `should return 403 when non-admin tries to list users`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "employer@firma.de",
                    passwordHash = passwordEncoder.encode("User-Password1!"),
                    firstName = "Normal",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/admin/users") {
                withAuth(token)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 401 when not authenticated for list users`() {
        // when & then
        mockMvc
            .get("/api/admin/users")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should disable user successfully`() {
        // given
        val adminRole = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = adminRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val employerRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = employerRole,
                ).apply { isEnabled = true },
            )
        val request = AdminUserFixtures.buildUpdateUserStatusRequest(enabled = false)

        // when & then
        mockMvc
            .patch("/api/admin/users/${user.id}/status") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNoContent() }
            }

        val updatedUser = userRepository.findById(user.id).get()
        assertThat(updatedUser.isEnabled).isFalse()
    }

    @Test
    fun `should enable user successfully`() {
        // given
        val adminRole = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = adminRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val employerRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = employerRole,
                ).apply { isEnabled = false },
            )
        val request = AdminUserFixtures.buildUpdateUserStatusRequest(enabled = true)

        // when & then
        mockMvc
            .patch("/api/admin/users/${user.id}/status") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNoContent() }
            }

        val updatedUser = userRepository.findById(user.id).get()
        assertThat(updatedUser.isEnabled).isTrue()
    }

    @Test
    fun `should return 404 when updating status for nonexistent user`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildUpdateUserStatusRequest()

        // when & then
        mockMvc
            .patch("/api/admin/users/nonexistent-id/status") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("USER_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when non-admin tries to update status`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "employer@firma.de",
                    passwordHash = passwordEncoder.encode("User-Password1!"),
                    firstName = "Normal",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request = AdminUserFixtures.buildUpdateUserStatusRequest()

        // when & then
        mockMvc
            .patch("/api/admin/users/some-id/status") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 401 when not authenticated for update status`() {
        // when & then
        mockMvc
            .patch("/api/admin/users/some-id/status") {
                withBodyRequest(AdminUserFixtures.buildUpdateUserStatusRequest())
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should update role successfully`() {
        // given
        val adminRole = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = adminRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val employerRole = roleRepository.save(RoleModel("EMPLOYER", null))
        roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = employerRole,
                ).apply { isEnabled = true },
            )
        val request = AdminUserFixtures.buildUpdateUserRoleRequest()

        // when & then
        mockMvc
            .patch("/api/admin/users/${user.id}/role") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNoContent() }
            }

        val updatedUser = userRepository.findById(user.id).get()
        assertThat(updatedUser.role.name).isEqualTo("PROJECTMANAGER")
    }

    @Test
    fun `should return 404 when updating role for nonexistent user`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildUpdateUserRoleRequest(role = "ADMIN")

        // when & then
        mockMvc
            .patch("/api/admin/users/nonexistent-id/role") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `should return 404 when role not found`() {
        // given
        val adminRole = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = adminRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val employerRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = employerRole,
                ).apply { isEnabled = true },
            )
        val request = AdminUserFixtures.buildUpdateUserRoleRequest(role = "NONEXISTENT")

        // when & then
        mockMvc
            .patch("/api/admin/users/${user.id}/role") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `should assign a custom role`() {
        // given: a role the company created itself, so it is not in RoleName
        val adminRole = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = adminRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val employerRole = roleRepository.save(RoleModel("EMPLOYER", null))
        roleRepository.save(RoleModel("AUDITOR", "Reads the audit log"))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = employerRole,
                ).apply { isEnabled = true },
            )

        // when
        mockMvc
            .patch("/api/admin/users/${user.id}/role") {
                withAuth(token)
                withBodyRequest(AdminUserFixtures.buildUpdateUserRoleRequest(role = "AUDITOR"))
            }.andExpect {
                status { isNoContent() }
            }

        // then
        val updatedUser = userRepository.findById(user.id).get()
        assertThat(updatedUser.role.name).isEqualTo("AUDITOR")
    }

    @Test
    fun `should return 400 when role is blank`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val admin =
            userRepository.save(
                UserModel(
                    email = "admin@firma.de",
                    passwordHash = passwordEncoder.encode("Admin-Password1!"),
                    firstName = "Admin",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(admin)
        val request = AdminUserFixtures.buildUpdateUserRoleRequest(role = "")

        // when & then
        mockMvc
            .patch("/api/admin/users/some-id/role") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 403 when non-admin tries to update role`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "employer@firma.de",
                    passwordHash = passwordEncoder.encode("User-Password1!"),
                    firstName = "Normal",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request = AdminUserFixtures.buildUpdateUserRoleRequest(role = "ADMIN")

        // when & then
        mockMvc
            .patch("/api/admin/users/some-id/role") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 401 when not authenticated for update role`() {
        // when & then
        mockMvc
            .patch("/api/admin/users/some-id/role") {
                withBodyRequest(AdminUserFixtures.buildUpdateUserRoleRequest(role = "ADMIN"))
            }.andExpect {
                status { isUnauthorized() }
            }
    }
}
