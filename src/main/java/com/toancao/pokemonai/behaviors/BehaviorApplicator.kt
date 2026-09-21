package com.toancao.pokemonai.behaviors

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.mixin.MobGoalSelectorAccessor
import com.toancao.pokemonai.registry.BehaviorRegistry
import com.toancao.pokemonai.utils.EntityUtils
import java.util.Collections
import java.util.WeakHashMap

object BehaviorApplicator {
    private const val LEGACY_GOALS_ADDED_TAG = "tc_behavior_goals_added"
    private val appliedEntities = Collections.newSetFromMap(WeakHashMap<PokemonEntity, Boolean>())

    fun register() {
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe { event ->
            apply(event.pokemonEntity)
        }

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            apply(event.entity)
        }
    }

    fun apply(entity: PokemonEntity) {
        if (entity.level().isClientSide) return
        entity.removeTag(LEGACY_GOALS_ADDED_TAG)
        if (entity in appliedEntities) return
        if (!EntityUtils.isWild(entity)) return

        val entries = BehaviorRegistry.getGoals(EntityUtils.getSpeciesName(entity))
        if (entries.isEmpty()) return

        val goalSelector = (entity as MobGoalSelectorAccessor).`tc_reborn$getGoalSelector`()
        entries.forEach { entry ->
            goalSelector.addGoal(entry.priority, entry.factory.create(entity))
        }
        appliedEntities.add(entity)
    }
}
