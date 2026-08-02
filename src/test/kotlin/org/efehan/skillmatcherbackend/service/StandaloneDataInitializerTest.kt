package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.properties.StandaloneProperties
import org.efehan.skillmatcherbackend.core.company.StandaloneDataInitializer
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.core.role.RoleService
import org.efehan.skillmatcherbackend.core.superadmin.SuperadminBootstrapInitializer.Companion.PLATFORM_COMPANY_ID
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.persistence.CompanyModel
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments

@DisplayName("Standalone Data Initializer Tests")
class StandaloneDataInitializerTest {
    private val companyRepository = mockk<CompanyRepository>()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>()
    private val invitationService = mockk<InvitationService>(relaxed = true)

    private val properties =
        StandaloneProperties(
            enabled = true,
            companyName = "Standalone GmbH",
            companyStreet = "Werkstraße 1",
            companyZip = "70173",
            companyCity = "Stuttgart",
            companyCountry = "DE",
            adminEmail = "admin@standalone.local",
        )

    @Test
    fun `platform anchor company is not mistaken for the customer tenant`() {
        val platform =
            CompanyModel(name = "Platform", street = "n/a", zip = "n/a", city = "n/a", country = "DE")
                .apply { id = PLATFORM_COMPANY_ID }
        val saved = slot<CompanyModel>()
        every { companyRepository.findAll() } returns listOf(platform)
        every { companyRepository.save(capture(saved)) } answers { saved.captured }
        every { roleRepository.findByCompanyIdAndName(any(), any()) } returns RoleModel(RoleName.ADMIN.name, null)
        every { userRepository.existsByEmail(any()) } returns false
        every { userRepository.save(any<UserModel>()) } answers { firstArg() }

        val userService = UserService(userRepository)
        StandaloneDataInitializer(
            properties,
            companyRepository,
            userService,
            RoleService(roleRepository, userService),
            invitationService,
        ).run(mockk<ApplicationArguments>())

        assertThat(saved.captured.name).isEqualTo("Standalone GmbH")
    }
}
