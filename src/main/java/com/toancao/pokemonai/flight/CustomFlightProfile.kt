package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import kotlin.random.Random

class CustomFlightProfile(
    pokemon: PokemonEntity,
    config: FlightConfig
) : BaseFlightSession(pokemon, config) {
    var agitation: Double = 0.0
    var spawnObserveTicks: Int = 0
    var isLegendary: Boolean = false
    var anchorX: Double = 0.0
    var anchorY: Double = 0.0
    var anchorZ: Double = 0.0

    var currentYaw: Double = Random.nextDouble() * 360.0
    var idleTicks: Int = 0
    var flightCount: Int = 0
    var verticalVelocity: Double = 0.0
    var speedBonus: Double = 0.25
    var currentPreferredHeight: Double = config.preferredHeight
    var currentStamina: Double = config.maxFlightTicks.toDouble()
    var bounceCount: Int = 0
    var staminaRecoveryMaxRate: Double = 0.10
    var debugTextDisplay: net.minecraft.world.entity.Display.TextDisplay? = null
    
    // Lưu tọa độ tâm khi thực hiện bay lượn vòng tròn
    var circularFlightCenter: net.minecraft.world.phys.Vec3? = null
    var circularFlightAngle: Double = 0.0

    fun refreshPreferredHeight() {
        currentPreferredHeight = config.preferredHeight + (Random.nextDouble() * 2.0 - 1.0)
    }
}
