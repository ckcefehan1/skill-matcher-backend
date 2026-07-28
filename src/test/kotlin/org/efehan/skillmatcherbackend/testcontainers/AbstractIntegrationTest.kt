package org.efehan.skillmatcherbackend.testcontainers

import org.efehan.skillmatcherbackend.TestcontainersConfiguration
import org.efehan.skillmatcherbackend.persistence.ChatMessageRepository
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.efehan.skillmatcherbackend.persistence.PasswordResetTokenRepository
import org.efehan.skillmatcherbackend.persistence.ProjectApplicationRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.SkillRelationRepository
import org.efehan.skillmatcherbackend.persistence.SkillRepository
import org.efehan.skillmatcherbackend.persistence.UserAvailabilityRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var roleRepository: RoleRepository

    @Autowired
    protected lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    protected lateinit var invitationTokenRepository: InvitationTokenRepository

    @Autowired
    protected lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired
    protected lateinit var userSkillRepository: UserSkillRepository

    @Autowired
    protected lateinit var skillRepository: SkillRepository

    @Autowired
    protected lateinit var skillRelationRepository: SkillRelationRepository

    @Autowired
    protected lateinit var projectSkillRepository: ProjectSkillRepository

    @Autowired
    protected lateinit var projectRepository: ProjectRepository

    @Autowired
    protected lateinit var projectMemberRepository: ProjectMemberRepository

    @Autowired
    protected lateinit var applicationRepository: ProjectApplicationRepository

    @Autowired
    protected lateinit var userAvailabilityRepository: UserAvailabilityRepository

    @Autowired
    protected lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    protected lateinit var conversationRepository: ConversationRepository

    @Autowired
    protected lateinit var cacheManager: CacheManager

    @BeforeEach
    fun cleanUp() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
        chatMessageRepository.deleteAll()
        conversationRepository.deleteAll()
        applicationRepository.deleteAll()
        projectMemberRepository.deleteAll()
        projectSkillRepository.deleteAll()
        projectRepository.deleteAll()
        userAvailabilityRepository.deleteAll()
        userSkillRepository.deleteAll()
        skillRelationRepository.deleteAll()
        skillRepository.deleteAll()
        passwordResetTokenRepository.deleteAll()
        invitationTokenRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
        roleRepository.deleteAll()
    }

    protected fun MockHttpServletRequestDsl.withBodyRequest(body: Any) {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(body)
        with(SecurityMockMvcRequestPostProcessors.csrf())
    }

    protected fun MockHttpServletRequestDsl.withAuth(token: String) {
        header("Authorization", "Bearer $token")
        with(SecurityMockMvcRequestPostProcessors.csrf())
    }
}
