package org.efehan.skillmatcherbackend.testcontainers

import org.efehan.skillmatcherbackend.TestcontainersConfiguration
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.CompanyBuilder
import org.efehan.skillmatcherbackend.persistence.ChatMessageRepository
import org.efehan.skillmatcherbackend.persistence.CompanyModel
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.efehan.skillmatcherbackend.persistence.NotificationRepository
import org.efehan.skillmatcherbackend.persistence.PasswordResetTokenRepository
import org.efehan.skillmatcherbackend.persistence.ProjectMemberRepository
import org.efehan.skillmatcherbackend.persistence.ProjectRepository
import org.efehan.skillmatcherbackend.persistence.ProjectSkillRepository
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleRepository
import org.efehan.skillmatcherbackend.persistence.SkillRepository
import org.efehan.skillmatcherbackend.persistence.UserAvailabilityRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.persistence.UserSkillRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.messaging.converter.JacksonJsonMessageConverter
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
abstract class AbstractWebSocketIntegrationTest {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var jsonMapper: JsonMapper

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
    protected lateinit var projectSkillRepository: ProjectSkillRepository

    @Autowired
    protected lateinit var projectRepository: ProjectRepository

    @Autowired
    protected lateinit var projectMemberRepository: ProjectMemberRepository

    @Autowired
    protected lateinit var userAvailabilityRepository: UserAvailabilityRepository

    @Autowired
    protected lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    protected lateinit var conversationRepository: ConversationRepository

    @Autowired
    protected lateinit var notificationRepository: NotificationRepository

    @Autowired
    protected lateinit var companyRepository: CompanyRepository

    protected lateinit var companyA: CompanyModel

    protected lateinit var stompClient: WebSocketStompClient

    private val sessions = mutableListOf<StompSession>()

    @BeforeEach
    fun setUp() {
        cleanUp()

        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = JacksonJsonMessageConverter(jsonMapper)
    }

    @AfterEach
    fun tearDown() {
        sessions.forEach { if (it.isConnected) it.disconnect() }
        sessions.clear()
        TenantContext.clear()
    }

    protected fun cleanUp() {
        TenantContext.runAsRoot {
            notificationRepository.deleteAll()
            chatMessageRepository.deleteAll()
            conversationRepository.deleteAll()
            projectMemberRepository.deleteAll()
            projectSkillRepository.deleteAll()
            projectRepository.deleteAll()
            userAvailabilityRepository.deleteAll()
            userSkillRepository.deleteAll()
            skillRepository.deleteAll()
            passwordResetTokenRepository.deleteAll()
            invitationTokenRepository.deleteAll()
            refreshTokenRepository.deleteAll()
            userRepository.deleteAll()
            roleRepository.deleteAll()
            companyRepository.deleteAll()

            companyA = companyRepository.save(CompanyBuilder().build(name = "Company A"))
        }
        TenantContext.set(companyA.id)
    }

    protected fun connectWithAuth(token: String): StompSession {
        val connectHeaders = StompHeaders()
        connectHeaders["Authorization"] = "Bearer $token"
        return connect(connectHeaders)
    }

    protected fun connectWithTicket(ticket: String): StompSession {
        val connectHeaders = StompHeaders()
        connectHeaders["ticket"] = ticket
        return connect(connectHeaders)
    }

    private fun connect(connectHeaders: StompHeaders): StompSession {
        val url = "ws://localhost:$port/ws"

        val session =
            stompClient
                .connectAsync(
                    url,
                    WebSocketHttpHeaders(),
                    connectHeaders,
                    object : StompSessionHandlerAdapter() {
                        override fun handleTransportError(
                            session: StompSession,
                            exception: Throwable,
                        ) {
                            System.err.println("WebSocket transport error: ${exception.message}")
                        }
                    },
                ).get(5, TimeUnit.SECONDS)

        sessions.add(session)
        return session
    }
}
