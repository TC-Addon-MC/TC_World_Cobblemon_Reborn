package com.toancao.pokemonai.spawner.hooks

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import java.util.UUID

fun interface PackSpawnCallback {
    fun onPackSpawned(herdId: UUID, leader: PokemonEntity, pack: List<PokemonEntity>)
}

object PackSpawnEvents {
    @JvmField
    val ON_PACK_SPAWNED: Event<PackSpawnCallback> = EventFactory.createArrayBacked(
        PackSpawnCallback::class.java
    ) { listeners ->
        PackSpawnCallback { herdId, leader, pack ->
            for (listener in listeners) {
                listener.onPackSpawned(herdId, leader, pack)
            }
        }
    }
}
