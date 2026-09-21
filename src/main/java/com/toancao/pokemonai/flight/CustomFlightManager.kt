package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.engine.FlightEngine
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import java.util.UUID

object CustomFlightManager {
    private val activeMachines = mutableMapOf<UUID, NormalFlightStateMachine>()
    private var scanCooldown = 0

    fun getMachine(uuid: UUID): NormalFlightStateMachine? {
        return activeMachines[uuid]
    }

    fun machineCount(): Int = activeMachines.size

    /**
     * Đợt 4: bật/tắt native navigation cho machine đang chạy của một loài,
     * phục vụ kiểm chứng gate Phase 0 mà không cần sửa JSON config.
     * @return số machine đã đổi.
     */
    fun setNativeForSpecies(speciesName: String, enabled: Boolean): Int {
        var count = 0
        for (machine in activeMachines.values) {
            try {
                if (!com.toancao.pokemonai.utils.EntityUtils.getSpeciesName(machine.pokemon).equals(speciesName, ignoreCase = true)) continue
                if (machine.profile.config.useNativeNavigation == enabled) continue
                machine.profile.config = machine.profile.config.copy(useNativeNavigation = enabled)
                if (!enabled) {
                    com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stop(machine.pokemon, keepFlying = true)
                }
                count++
            } catch (_: Exception) {
            }
        }
        return count
    }

    fun forceAddMachine(entity: PokemonEntity, state: com.toancao.pokemonai.flight.FlightState, config: FlightConfig? = null) {
        val finalConfig = config ?: CustomFlightRegistry.getConfigs(net.minecraft.resources.ResourceLocation.tryParse("cobblemon:${com.toancao.pokemonai.utils.EntityUtils.getSpeciesName(entity).lowercase()}")!!)?.random() ?: FlightConfig()
        val machine = NormalFlightStateMachine(entity, state, finalConfig)
        
        machine.profile.currentYaw = entity.yRot.toDouble()
        
        machine.profile.currentStamina = machine.profile.config.maxFlightTicks.toDouble()
        
        machine.markPlayerSeen()
        activeMachines[entity.uuid] = machine
    }

    /**
     * Phase 5: attach idempotent theo entity lifecycle, không ghi tag persistent.
     */
    fun tryAttach(entity: PokemonEntity): Boolean {
        if (entity.level().isClientSide) return false
        if (!entity.isAlive || entity.isRemoved) return false
        if (activeMachines.containsKey(entity.uuid)) return true
        if (!com.toancao.pokemonai.utils.EntityUtils.isWild(entity)) return false
        val speciesName = com.toancao.pokemonai.utils.EntityUtils.getSpeciesName(entity)
        val sanitizedName = speciesName.lowercase().replace("[^a-z0-9/._-]".toRegex(), "")
        val speciesId = net.minecraft.resources.ResourceLocation.tryParse("cobblemon:$sanitizedName")
            ?: return false
        if (!CustomFlightRegistry.hasConfig(speciesId)) return false
        val config = CustomFlightRegistry.getConfigs(speciesId)?.random() ?: return false
        val isHighUp = !entity.onGround() && !entity.isInWater && !entity.isUnderWater
        val initialState = if (isHighUp) FlightState.FLYING else FlightState.PERCHING
        val machine = NormalFlightStateMachine(entity, initialState, config)
        machine.markPlayerSeen()
        activeMachines[entity.uuid] = machine
        return true
    }

    fun detach(uuid: java.util.UUID) {
        val machine = activeMachines.remove(uuid)
        machine?.cleanup()
        FlightEngine.removeForUnload(uuid)
        try {
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stopForUnload(uuid)
        } catch (_: Exception) {
        }
    }

    fun register() {
        FlightEngine.register()
        CobblemonEvents.RIDE_EVENT_PRE.subscribe { event ->
            FlightEngine.suspendForRiding(event.pokemon)
        }
        CobblemonEvents.RIDE_EVENT_POST.subscribe { event ->
            FlightEngine.suspendForRiding(event.pokemon)
        }
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            try {
                tryAttach(event.entity)
            } catch (_: Exception) {
            }
        }
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe { event ->
            try {
                tryAttach(event.pokemonEntity)
            } catch (_: Exception) {
            }
        }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is PokemonEntity) {
                detach(entity.uuid)
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            com.toancao.pokemonai.flight.spawner.CustomAirSpawner.tick(server)
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.tickGlobal()
            scanCooldown--
            if (scanCooldown <= 0) {
                scanCooldown = com.toancao.pokemonai.config.FlightConfigManager.machineScanInterval
                
                for (player in server.playerList.players) {
                    val level = player.level()
                    val box = player.boundingBox.inflate(120.0)
                    val entities = level.getEntitiesOfClass(PokemonEntity::class.java, box)
                    
                    for (entity in entities) {
                        if (!entity.isAlive || entity.isRemoved) continue
                        val uuid = entity.uuid
                        
                        if (activeMachines.containsKey(uuid)) {
                            activeMachines[uuid]!!.markPlayerSeen()
                            continue
                        }

                        tryAttach(entity)
                    }
                }
            }

            val toRemove = mutableListOf<Pair<UUID, NormalFlightStateMachine>>()
            val unloadDelay = com.toancao.pokemonai.config.FlightConfigManager.machineUnloadDelay

            for ((uuid, machine) in activeMachines.entries.toList()) {
                if (activeMachines[uuid] !== machine) continue
                if (!machine.isAlive()) {
                    machine.cleanup()
                    toRemove.add(uuid to machine)
                } else {
                    machine.tick()

                    if (activeMachines[uuid] === machine && machine.canBeRemovedSafely(unloadDelay)) {
                        machine.cleanup()
                        toRemove.add(uuid to machine)
                    }
                }
            }

            toRemove.forEach { (uuid, machine) ->
                if (activeMachines[uuid] === machine) {
                    activeMachines.remove(uuid)
                }
            }
        }
    }
}
