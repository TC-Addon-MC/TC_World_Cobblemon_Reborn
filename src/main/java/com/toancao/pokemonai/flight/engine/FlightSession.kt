package com.toancao.pokemonai.flight.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.BaseFlightSession
import com.toancao.pokemonai.flight.FlightConfig
import net.minecraft.world.phys.Vec3

// Trạng thái nội bộ của một session bay có chỉ định (4 state tối thiểu, không có arc)
enum class InternalFlightState { FLYING, ARRIVED_HOVER, FALLING, DONE }

// Chứa toàn bộ dữ liệu runtime của một lần bay trong FlightEngine
class FlightSession(
    pokemon: PokemonEntity,
    var target: Vec3,
    var hover: Boolean,
    config: FlightConfig
) : BaseFlightSession(pokemon, config) {
    var state: InternalFlightState = InternalFlightState.FLYING
    var isSearchingLand: Boolean = false
    var needsBounce: Boolean = false
}
