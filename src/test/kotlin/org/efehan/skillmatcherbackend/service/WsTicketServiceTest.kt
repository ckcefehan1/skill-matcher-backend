package org.efehan.skillmatcherbackend.service

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.properties.WsTicketProperties
import org.efehan.skillmatcherbackend.core.auth.WsTicketService
import org.junit.jupiter.api.Test
import java.time.Duration

class WsTicketServiceTest {
    private val service = WsTicketService(WsTicketProperties(ttl = Duration.ofSeconds(60), maxSize = 100))

    @Test
    fun `issued ticket resolves to user id once`() {
        val ticket = service.issue("user-1")

        assertThat(service.consume(ticket)).isEqualTo("user-1")
    }

    @Test
    fun `ticket is one-time use`() {
        val ticket = service.issue("user-1")

        service.consume(ticket)

        assertThat(service.consume(ticket)).isNull()
    }

    @Test
    fun `unknown ticket resolves to null`() {
        assertThat(service.consume("does-not-exist")).isNull()
    }

    @Test
    fun `tickets are unique per issue`() {
        val first = service.issue("user-1")
        val second = service.issue("user-1")

        assertThat(first).isNotEqualTo(second)
        assertThat(service.consume(first)).isEqualTo("user-1")
        assertThat(service.consume(second)).isEqualTo("user-1")
    }
}
