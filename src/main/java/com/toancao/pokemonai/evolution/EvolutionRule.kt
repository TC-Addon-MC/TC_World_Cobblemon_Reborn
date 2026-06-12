package com.toancao.pokemonai.evolution

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.emotions.EmotionComponent
import com.toancao.pokemonai.attachment.EvolutionStateData

data class EvolutionResult(val targetSpecies: String, val modifiers: (PokemonEntity) -> Unit = {})

interface EvolutionRule {
    fun check(entity: PokemonEntity, emotion: EmotionComponent, state: EvolutionStateData): EvolutionResult?
}



