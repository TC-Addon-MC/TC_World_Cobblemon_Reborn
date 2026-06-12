@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.evolution.rules

import com.toancao.pokemonai.evolution.EvolutionRule
import com.toancao.pokemonai.evolution.EvolutionResult
import com.toancao.pokemonai.emotions.EmotionComponent
import com.toancao.pokemonai.attachment.EvolutionStateData
import com.toancao.pokemonai.utils.EntityUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.Entity

class MagikarpDeterminationRule : EvolutionRule {
    override fun check(entity: PokemonEntity, emotion: EmotionComponent, state: EvolutionStateData): EvolutionResult? {
        if (emotion.determination >= 100 && state.dragonGatePassed) {
            return EvolutionResult("gyarados")
        }
        return null
    }
}

class MagikarpRageRule : EvolutionRule {
    override fun check(entity: PokemonEntity, emotion: EmotionComponent, state: EvolutionStateData): EvolutionResult? {
        if (emotion.rage >= 100) {
            return EvolutionResult("gyarados") { evolvedEntity ->
                val e = evolvedEntity as Entity
                val s = EntityUtils.getEvolutionState(e)
                s.dragonGatePassed = true
                EntityUtils.setEvolutionState(e, s)
            }
        }
        return null
    }
}

