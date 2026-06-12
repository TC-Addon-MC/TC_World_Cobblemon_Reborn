@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.utils

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.attachment.EvolutionStateData
import com.toancao.pokemonai.attachment.PokemonAttachments
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.emotions.EmotionComponent
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.minecraft.world.entity.Entity

object EntityUtils {
    fun isWild(entity: PokemonEntity): Boolean {
        return CobblemonBridge.isWild(entity)
    }

    fun getEmotion(entity: Entity): EmotionComponent {
        val target = entity as AttachmentTarget
        var emotion = target.getAttached(PokemonAttachments.EMOTION)
        if (emotion == null) {
            emotion = EmotionComponent()
            target.setAttached(PokemonAttachments.EMOTION, emotion)
        }
        return emotion
    }

    fun setEmotion(entity: Entity, emotion: EmotionComponent) {
        (entity as AttachmentTarget).setAttached(PokemonAttachments.EMOTION, emotion)
    }

    fun getEvolutionState(entity: Entity): EvolutionStateData {
        val target = entity as AttachmentTarget
        var state = target.getAttached(PokemonAttachments.EVOLUTION_STATE)
        if (state == null) {
            state = EvolutionStateData()
            target.setAttached(PokemonAttachments.EVOLUTION_STATE, state)
        }
        return state
    }

    fun setEvolutionState(entity: Entity, state: EvolutionStateData) {
        (entity as AttachmentTarget).setAttached(PokemonAttachments.EVOLUTION_STATE, state)
    }

    fun getSpeciesName(entity: PokemonEntity): String {
        return CobblemonBridge.getSpeciesName(entity)
    }
}
