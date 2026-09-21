package com.toancao.pokemonai.pokemon

import com.toancao.pokemonai.behaviors.forage.IronCravingGoal
import com.toancao.pokemonai.registry.BehaviorRegistry

object AronConfig {
    const val SPECIES = "aron"

    val behaviors = listOf(
        BehaviorRegistry.Entry(2, { entity -> IronCravingGoal(entity) })
    )
}
