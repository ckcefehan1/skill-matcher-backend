package org.efehan.skillmatcherbackend.core.chat

interface ChatEventPublisher {
    fun publish(event: ChatEvent)
}
