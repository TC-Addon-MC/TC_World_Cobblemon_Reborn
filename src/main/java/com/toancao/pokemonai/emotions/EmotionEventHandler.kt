@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.emotions

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity as CE
import com.toancao.pokemonai.attachment.EvolutionStateData
import com.toancao.pokemonai.utils.EntityUtils
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

object EmotionEventHandler {
    fun register() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register { world, killer, killed ->
            // placeholder
        }
    }

    fun onDragonGateSuccess(entity: PokemonEntity) {
        val e = entity as Entity
        val emotion = EntityUtils.getEmotion(e)
        emotion.determination = 100
        EntityUtils.setEmotion(e, emotion)

        val state = EntityUtils.getEvolutionState(e)
        state.dragonGatePassed = true
        EntityUtils.setEvolutionState(e, state)
    }

    fun onDragonGateFailed(entity: PokemonEntity) {
        val e = entity as Entity
        val emotion = EntityUtils.getEmotion(e)
        emotion.determination = (emotion.determination + 10).coerceAtMost(100)
        EntityUtils.setEmotion(e, emotion)
    }

    fun onDamagedByPlayer(entity: PokemonEntity, player: Player) {
        val e = entity as Entity
        val emotion = EntityUtils.getEmotion(e)
        emotion.fear = (emotion.fear + 15).coerceAtMost(100)
        emotion.rage = (emotion.rage + 20).coerceAtMost(100)
        EntityUtils.setEmotion(e, emotion)
    }
}

