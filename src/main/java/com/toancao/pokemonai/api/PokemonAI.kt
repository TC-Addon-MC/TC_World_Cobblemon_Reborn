package com.toancao.pokemonai.api

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.engine.FlightEngine
import net.minecraft.world.phys.Vec3

/**
 * Main public API entry point for PokemonAI.
 */
object PokemonAI {
    /**
     * Forces a Pokemon to start a custom flight session towards a specific coordinate.
     * @param pokemon The Pokemon entity to command
     * @param target The target location vector
     * @param hover Whether the Pokemon should hover upon arrival instead of landing
     * @return true if the flight session was successfully initiated
     */
    @JvmStatic
    @JvmOverloads
    fun flyTo(pokemon: PokemonEntity, target: Vec3, hover: Boolean = false): Boolean {
        return FlightEngine.flyTo(pokemon, target, hover)
    }

    /**
     * Stops the active flight session for the given Pokemon and forces it to land.
     */
    @JvmStatic
    fun stopFlight(pokemon: PokemonEntity) {
        FlightEngine.stopFlight(pokemon)
    }

    /**
     * Checks if a Pokemon is currently in an active custom flight session.
     */
    @JvmStatic
    fun isFlying(pokemon: PokemonEntity): Boolean {
        return FlightEngine.hasActiveFlight(pokemon)
    }

    // ==========================================
    // Flight Stamina & Mechanics
    // ==========================================

    @JvmStatic
    fun getRemainingStamina(pokemon: PokemonEntity): Float {
        val machine = com.toancao.pokemonai.flight.CustomFlightManager.getMachine(pokemon.uuid)
        return machine?.profile?.currentStamina?.toFloat() ?: 0f
    }

    @JvmStatic
    fun setStamina(pokemon: PokemonEntity, amount: Float) {
        val machine = com.toancao.pokemonai.flight.CustomFlightManager.getMachine(pokemon.uuid)
        if (machine != null) {
            machine.profile.currentStamina = amount.toDouble()
        }
    }

    @JvmStatic
    fun forceCircularFlight(pokemon: PokemonEntity, centerPos: Vec3) {
        val machine = com.toancao.pokemonai.flight.CustomFlightManager.getMachine(pokemon.uuid)
        if (machine != null) {
            machine.profile.currentStamina = machine.profile.config.maxFlightTicks.toDouble() // Fill stamina
            machine.profile.circularFlightCenter = centerPos
            machine.transitionTo(com.toancao.pokemonai.flight.FlightState.CIRCULAR_FLYING)
        }
    }

    @JvmStatic
    fun forceHover(pokemon: PokemonEntity) {
        val machine = com.toancao.pokemonai.flight.CustomFlightManager.getMachine(pokemon.uuid)
        if (machine != null) {
            machine.transitionTo(com.toancao.pokemonai.flight.FlightState.GROUND_HOVERING)
        }
    }

    // ==========================================
    // Dragon Gate Events
    // ==========================================

    @JvmStatic
    fun startDragonGateEvent(level: net.minecraft.server.level.ServerLevel) {
        if (!isDragonGateEventRunning()) {
            com.toancao.pokemonai.events.DragonGateEvent.trigger(level)
        }
    }

    @JvmStatic
    fun stopDragonGateEvent(level: net.minecraft.server.level.ServerLevel) {
        if (isDragonGateEventRunning()) {
            com.toancao.pokemonai.events.DragonGateEvent.stop(level)
        }
    }

    @JvmStatic
    fun isDragonGateEventRunning(): Boolean {
        return com.toancao.pokemonai.events.DragonGateEvent.currentPhase != com.toancao.pokemonai.events.DragonGateEvent.EventPhase.IDLE
    }

    // ==========================================
    // Air Spawner
    // ==========================================

    @JvmStatic
    fun spawnCloudPokemon(player: net.minecraft.server.level.ServerPlayer, targetSpecies: String) {
        val level = player.serverLevel()
        val pos = player.blockPosition().above(6 + level.random.nextInt(4))
        com.toancao.pokemonai.flight.spawner.CustomAirSpawner.spawnExactPokemon(level, pos, targetSpecies)
    }

    // ==========================================
    // AI Filter
    // ==========================================

    @JvmStatic
    fun isEligibleForCustomAI(pokemon: PokemonEntity): Boolean {
        return com.toancao.pokemonai.utils.AIFilter.isEligible(pokemon)
    }
}
