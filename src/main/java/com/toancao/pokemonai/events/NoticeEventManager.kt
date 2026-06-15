package com.toancao.pokemonai.events

import net.minecraft.server.level.ServerLevel

object NoticeEventManager {
    data class NoticeEvent(
        val title: String,
        val subtitle: String,
        val desc: String,
        val remainingTicks: Long
    )

    private val providers = mutableListOf<(ServerLevel) -> List<NoticeEvent>>()

    fun registerProvider(provider: (ServerLevel) -> List<NoticeEvent>) {
        providers.add(provider)
    }

    fun getAllEvents(level: ServerLevel): List<NoticeEvent> {
        return providers.flatMap { it(level) }
    }
}
