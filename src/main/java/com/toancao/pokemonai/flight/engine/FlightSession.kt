package com.toancao.pokemonai.flight.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.BaseFlightSession
import com.toancao.pokemonai.flight.FlightConfig
import net.minecraft.world.phys.Vec3

enum class InternalFlightState { FLYING, ARRIVED_HOVER, FALLING, DONE }

class FlightSession(
    pokemon: PokemonEntity,
    var target: Vec3,
    var hover: Boolean,
    config: FlightConfig
) : BaseFlightSession(pokemon, config) {
    var state: InternalFlightState = InternalFlightState.FLYING
    var isSearchingLand: Boolean = false
    var needsBounce: Boolean = false
    var owner: FlightControlOwner = FlightControlOwner.AUTONOMOUS
    /** Số lần tìm lại điểm đáp cho cùng một ý định hạ cánh; chống loop vô hạn. */
    var landRetries: Int = 0
}
