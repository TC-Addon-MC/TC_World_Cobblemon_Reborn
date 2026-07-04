package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.engine.FlightEngine
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import java.util.UUID

object CustomFlightManager {
    private val activeMachines = mutableMapOf<UUID, NormalFlightStateMachine>()

    fun getMachine(uuid: UUID): NormalFlightStateMachine? {
        return activeMachines[uuid]
    }

    fun register() {
        FlightEngine.register()

        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity !is PokemonEntity) return@register

            val speciesName = com.toancao.pokemonai.utils.EntityUtils.getSpeciesName(entity)
            val sanitizedName = speciesName.lowercase().replace("[^a-z0-9/._-]".toRegex(), "")
            val speciesId = net.minecraft.resources.ResourceLocation.tryParse("cobblemon:$sanitizedName")
            if (speciesId == null || !CustomFlightRegistry.hasConfig(speciesId)) return@register

            world.server.execute {
                // Đảm bảo xoá sạch trạng thái vật lý lơ lửng nếu entity bị lưu vào NBT trong lúc đang bay
                FlightHelpers.terminateFlight(entity)

                val configs = CustomFlightRegistry.getConfigs(speciesId)!!
                val config = configs.random()
                // Xác suất bay ngay khi được gọi/sinh ra (gấp đôi baseTakeoffChance để dễ bay hơn lúc mới ra)
                val initialState = if (kotlin.random.Random.nextDouble() < config.baseTakeoffChance * 2.0) {
                    FlightState.TAKING_OFF
                } else {
                    FlightState.PERCHING
                }
                val machine = NormalFlightStateMachine(entity, initialState, config)
                activeMachines[entity.uuid] = machine
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            val toRemove = mutableListOf<UUID>()

            for ((uuid, machine) in activeMachines) {
                if (!machine.isAlive()) {
                    machine.cleanup()
                    toRemove.add(uuid)
                } else {
                    machine.tick()
                }
            }

            toRemove.forEach { activeMachines.remove(it) }
        }
    }
}
