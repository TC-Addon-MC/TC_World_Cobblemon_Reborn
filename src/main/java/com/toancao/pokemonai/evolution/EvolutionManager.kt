@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.evolution

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.utils.EntityUtils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

object EvolutionManager {
    fun register() {
        var tickCounter = 0
        ServerTickEvents.END_WORLD_TICK.register { world: ServerLevel ->
            tickCounter++
            if (tickCounter >= 20) {
                tickCounter = 0
                tick(world)
            }
        }
    }

    private fun tick(world: ServerLevel) {
        world.getAllEntities().forEach { entity ->
            if (CobblemonBridge.checkIsPokemonEntity(entity)) {
                val pokemon = CobblemonBridge.castToPokemonEntity(entity)
                if (CobblemonBridge.isWild(pokemon)) {
                    checkEvolution(pokemon, entity)
                }
            }
        }
    }

    private fun checkEvolution(pokemon: PokemonEntity, entity: Entity) {
        val species = CobblemonBridge.getSpeciesName(pokemon)
        val rules = EvolutionRegistry.getRules(species)
        if (rules.isEmpty()) return

        val emotion = EntityUtils.getEmotion(entity)
        val state = EntityUtils.getEvolutionState(entity)

        for (rule in rules) {
            val result = rule.check(pokemon, emotion, state)
            if (result != null) {
                evolve(pokemon, result)
                break
            }
        }
    }

    private fun evolve(pokemon: PokemonEntity, result: EvolutionResult) {
        val pokemonData = CobblemonBridge.getPokemonData(pokemon)
        val evolution = pokemonData.species.evolutions.find {
            it.result.species?.lowercase() == result.targetSpecies.lowercase()
        }
        if (evolution != null) {
            pokemonData.evolutionProxy.server().start(evolution)
            result.modifiers(pokemon)
        }
    }

    fun forceEvolve(pokemon: PokemonEntity, targetSpecies: String) {
        val pokemonData = CobblemonBridge.getPokemonData(pokemon)
        val evolution = pokemonData.species.evolutions.find {
            it.result.species?.lowercase() == targetSpecies.lowercase()
        }
        if (evolution != null) {
            pokemonData.evolutionProxy.server().start(evolution)
        }
    }
}
