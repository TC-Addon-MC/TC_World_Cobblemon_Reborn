package com.toancao.pokemonai.api

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.world.phys.Vec3

/**
 * Public Events for PokemonAI.
 * Third-party mods can register to these events to interact with PokemonAI's custom behaviors.
 */
object PokemonAIEvents {
    
    // ==========================================
    // Flight Events
    // ==========================================

    /**
     * Fired when a Pokemon is about to start a flight session.
     * Return false to cancel the flight, true to allow it.
     */
    @JvmField
    val FLIGHT_START: Event<FlightStartCallback> = EventFactory.createArrayBacked(FlightStartCallback::class.java) { listeners ->
        FlightStartCallback { pokemon, target, hover ->
            for (listener in listeners) {
                if (!listener.onFlightStart(pokemon, target, hover)) {
                    return@FlightStartCallback false
                }
            }
            true
        }
    }

    /**
     * Fired when a Pokemon finishes or cancels its flight session (e.g., lands).
     */
    @JvmField
    val FLIGHT_END: Event<FlightEndCallback> = EventFactory.createArrayBacked(FlightEndCallback::class.java) { listeners ->
        FlightEndCallback { pokemon ->
            for (listener in listeners) {
                listener.onFlightEnd(pokemon)
            }
        }
    }

    // ==========================================
    // Evolution & Challenge Events
    // ==========================================

    /**
     * Fired when a Pokemon is about to forcefully evolve via PokemonAI (e.g., Magikarp jumping the Dragon Gate).
     * Return false to prevent the evolution.
     */
    @JvmField
    val BEFORE_FORCE_EVOLVE: Event<BeforeForceEvolveCallback> = EventFactory.createArrayBacked(BeforeForceEvolveCallback::class.java) { listeners ->
        BeforeForceEvolveCallback { pokemon, targetSpecies ->
            for (listener in listeners) {
                if (!listener.onBeforeForceEvolve(pokemon, targetSpecies)) {
                    return@BeforeForceEvolveCallback false
                }
            }
            true
        }
    }

    /**
     * Fired immediately after a Pokemon successfully force-evolves.
     */
    @JvmField
    val AFTER_FORCE_EVOLVE: Event<AfterForceEvolveCallback> = EventFactory.createArrayBacked(AfterForceEvolveCallback::class.java) { listeners ->
        AfterForceEvolveCallback { pokemon, newSpecies ->
            for (listener in listeners) {
                listener.onAfterForceEvolve(pokemon, newSpecies)
            }
        }
    }

    /**
     * Fired when the Dragon Gate challenge event is about to start.
     * Return false to prevent the event from starting.
     */
    @JvmField
    val DRAGON_GATE_START: Event<DragonGateStartCallback> = EventFactory.createArrayBacked(DragonGateStartCallback::class.java) { listeners ->
        DragonGateStartCallback { level ->
            for (listener in listeners) {
                if (!listener.onDragonGateStart(level)) {
                    return@DragonGateStartCallback false
                }
            }
            true
        }
    }

    /**
     * Fired when the Dragon Gate challenge event ends.
     */
    @JvmField
    val DRAGON_GATE_END: Event<DragonGateEndCallback> = EventFactory.createArrayBacked(DragonGateEndCallback::class.java) { listeners ->
        DragonGateEndCallback { level ->
            for (listener in listeners) {
                listener.onDragonGateEnd(level)
            }
        }
    }

    /**
     * Fired just before a Magikarp performs a dynamic jump from water.
     * Listeners can return a modified float value representing jump velocity.
     * Return 0.0f to cancel the jump entirely.
     */
    @JvmField
    val ON_MAGIKARP_JUMP: Event<MagikarpJumpCallback> = EventFactory.createArrayBacked(MagikarpJumpCallback::class.java) { listeners ->
        MagikarpJumpCallback { pokemon, originalVelocity ->
            var finalVelocity = originalVelocity
            for (listener in listeners) {
                finalVelocity = listener.onMagikarpJump(pokemon, finalVelocity)
                if (finalVelocity <= 0.0f) break
            }
            finalVelocity
        }
    }

    // ==========================================
    // Air Spawner Events
    // ==========================================

    /**
     * Fired when CustomAirSpawner is about to spawn a pokemon on a cloud.
     * Modifiers can change the target species by returning a new species string, or return null to cancel the spawn.
     */
    @JvmField
    val ON_AIR_SPAWN: Event<AirSpawnCallback> = EventFactory.createArrayBacked(AirSpawnCallback::class.java) { listeners ->
        AirSpawnCallback { player, targetSpecies ->
            var finalSpecies: String? = targetSpecies
            for (listener in listeners) {
                finalSpecies = listener.onAirSpawn(player, finalSpecies)
                if (finalSpecies == null) break
            }
            finalSpecies
        }
    }

    // ==========================================
    // AI Filter Events
    // ==========================================

    /**
     * Fired during AIFilter checks to see if a Pokemon is eligible for natural/custom AI actions.
     * Return false to block the Pokemon from performing custom AI actions (e.g. if another mod wants to pause it).
     */
    @JvmField
    val ON_AI_FILTER_CHECK: Event<AIFilterCheckCallback> = EventFactory.createArrayBacked(AIFilterCheckCallback::class.java) { listeners ->
        AIFilterCheckCallback { pokemon ->
            for (listener in listeners) {
                if (!listener.onAIFilterCheck(pokemon)) {
                    return@AIFilterCheckCallback false
                }
            }
            true
        }
    }

    // ==========================================
    // Interfaces
    // ==========================================

    fun interface FlightStartCallback {
        fun onFlightStart(pokemon: PokemonEntity, target: Vec3, isHovering: Boolean): Boolean
    }

    fun interface FlightEndCallback {
        fun onFlightEnd(pokemon: PokemonEntity)
    }

    fun interface BeforeForceEvolveCallback {
        fun onBeforeForceEvolve(pokemon: PokemonEntity, targetSpecies: String): Boolean
    }

    fun interface AfterForceEvolveCallback {
        fun onAfterForceEvolve(pokemon: PokemonEntity, newSpecies: String)
    }

    fun interface DragonGateStartCallback {
        fun onDragonGateStart(level: net.minecraft.server.level.ServerLevel): Boolean
    }

    fun interface DragonGateEndCallback {
        fun onDragonGateEnd(level: net.minecraft.server.level.ServerLevel)
    }

    fun interface MagikarpJumpCallback {
        fun onMagikarpJump(pokemon: PokemonEntity, originalVelocity: Float): Float
    }

    fun interface AirSpawnCallback {
        fun onAirSpawn(player: net.minecraft.server.level.ServerPlayer, targetSpecies: String?): String?
    }

    fun interface AIFilterCheckCallback {
        fun onAIFilterCheck(pokemon: PokemonEntity): Boolean
    }
}
