package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity

abstract class BaseFlightSession(
    val pokemon: PokemonEntity,
    var config: FlightConfig
) {
    var ticksInCurrentState: Int = 0

    open fun isAlive(): Boolean {
        val mob = pokemon as? net.minecraft.world.entity.Mob ?: return false
        return mob.isAlive && !mob.isRemoved && mob.level() != null
    }
}
