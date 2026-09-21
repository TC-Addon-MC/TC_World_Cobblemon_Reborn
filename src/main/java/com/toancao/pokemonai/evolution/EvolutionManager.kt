@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.evolution

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.config.MagikarpConfigManager
import com.toancao.pokemonai.utils.EntityUtils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

object EvolutionManager {
    class ScheduledTask(var delayTicks: Int, val action: () -> Unit)
    private val scheduledTasks = mutableListOf<ScheduledTask>()

    private data class PendingEvolution(val targetSpecies: String, val modifiers: (PokemonEntity) -> Unit)
    private val evolvingUntil = mutableMapOf<java.util.UUID, Long>()
    private val pendingEvolutions = mutableMapOf<java.util.UUID, PendingEvolution>()

    fun scheduleTask(delayTicks: Int, action: () -> Unit) {
        scheduledTasks.add(ScheduledTask(delayTicks, action))
    }

    fun register() {
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe { event ->
            val entity = event.pokemon.entity ?: return@subscribe
            val pending = pendingEvolutions.remove(event.pokemon.uuid) ?: return@subscribe
            if (event.pokemon.species.name.equals(pending.targetSpecies, ignoreCase = true)) {
                pending.modifiers(entity)
                com.toancao.pokemonai.api.PokemonAIEvents.AFTER_FORCE_EVOLVE.invoker()
                    .onAfterForceEvolve(entity, pending.targetSpecies)
            }
            evolvingUntil.remove(event.pokemon.uuid)
        }

        var tickCounter = 0
        ServerTickEvents.END_WORLD_TICK.register { world: ServerLevel ->
            val toExecute = mutableListOf<ScheduledTask>()
            val iterator = scheduledTasks.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                task.delayTicks--
                if (task.delayTicks <= 0) {
                    iterator.remove()
                    toExecute.add(task)
                }
            }
            toExecute.forEach { it.action() }

            tickCounter++
            if (tickCounter >= 20) {
                tickCounter = 0
                tick(world)
            }
        }
    }

    private fun tick(world: ServerLevel) {
        val entities = world.getAllEntities().toList()
        entities.forEach { entity ->
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
        forceEvolve(pokemon, result.targetSpecies, result.modifiers)
    }

    fun forceEvolve(
        pokemon: PokemonEntity,
        targetSpecies: String,
        modifiers: (PokemonEntity) -> Unit = {}
    ) {
        if (!CobblemonBridge.isWild(pokemon)) return

        val allow = com.toancao.pokemonai.api.PokemonAIEvents.BEFORE_FORCE_EVOLVE.invoker().onBeforeForceEvolve(pokemon, targetSpecies)
        if (!allow) return

        val pokemonId = CobblemonBridge.getEntityUUID(pokemon)
        val level = CobblemonBridge.getLevel(pokemon) as? ServerLevel ?: return
        if ((evolvingUntil[pokemonId] ?: 0L) > level.gameTime) return

        val pokemonData = CobblemonBridge.getPokemonData(pokemon)
        val candidates = if (MagikarpConfigManager.config.forceEvolutionIgnoresRequirements) {
            pokemonData.evolutions + pokemonData.lockedEvolutions
        } else {
            pokemonData.evolutions
        }
        val evolution = candidates.firstOrNull {
            it.result.species?.equals(targetSpecies, ignoreCase = true) == true
        } ?: return

        evolvingUntil[pokemonId] = level.gameTime + 200L
        pendingEvolutions[pokemonId] = PendingEvolution(targetSpecies, modifiers)
        if (MagikarpConfigManager.config.forceEvolutionIgnoresRequirements) {
            evolution.forceEvolve(pokemonData)
        } else if (!evolution.evolve(pokemonData)) {
            evolvingUntil.remove(pokemonId)
            pendingEvolutions.remove(pokemonId)
        }
    }
}
