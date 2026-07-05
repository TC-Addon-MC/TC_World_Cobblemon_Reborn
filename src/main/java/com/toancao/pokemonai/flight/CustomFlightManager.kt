package com.toancao.pokemonai.flight

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

    fun forceAddMachine(entity: PokemonEntity, state: com.toancao.pokemonai.flight.FlightState, config: FlightConfig? = null) {
        val finalConfig = config ?: CustomFlightRegistry.getConfigs(net.minecraft.resources.ResourceLocation.tryParse("cobblemon:${com.toancao.pokemonai.utils.EntityUtils.getSpeciesName(entity).lowercase()}")!!)?.random() ?: FlightConfig()
        val machine = NormalFlightStateMachine(entity, state, finalConfig)
        
        // Đồng bộ hướng bay (yaw) để nếu entity được thiết lập sẵn hướng nhìn (ví dụ nhìn về phía người chơi lúc spawn),
        // thì profile bay cũng sẽ nhắm thẳng về hướng đó.
        machine.profile.currentYaw = entity.yRot.toDouble()
        
        // Bơm đầy stamina cho Pokemon ngay khi spawn/thêm vào hệ thống bay
        machine.profile.currentStamina = machine.profile.config.maxFlightTicks.toDouble()
        
        machine.markPlayerSeen()
        activeMachines[entity.uuid] = machine
    }

    fun register() {
        FlightEngine.register()

        ServerTickEvents.END_SERVER_TICK.register { server ->
            com.toancao.pokemonai.flight.spawner.CustomAirSpawner.tick(server)
            // 1. Quét định kỳ quanh người chơi để tạo machine cho Pokemon chưa có
            scanCooldown--
            if (scanCooldown <= 0) {
                scanCooldown = com.toancao.pokemonai.config.FlightConfigManager.machineScanInterval
                
                for (player in server.playerList.players) {
                    val level = player.level()
                    val box = player.boundingBox.inflate(120.0) // Bán kính tương đương activationRadius tối đa
                    val entities = level.getEntitiesOfClass(PokemonEntity::class.java, box)
                    
                    for (entity in entities) {
                        if (!entity.isAlive || entity.isRemoved) continue
                        val uuid = entity.uuid
                        
                        if (activeMachines.containsKey(uuid)) {
                            // Đã có machine -> Báo cho machine biết nó vừa được người chơi nhìn thấy
                            activeMachines[uuid]!!.markPlayerSeen()
                            continue
                        }

                        val speciesName = com.toancao.pokemonai.utils.EntityUtils.getSpeciesName(entity)
                        val sanitizedName = speciesName.lowercase().replace("[^a-z0-9/._-]".toRegex(), "")
                        val speciesId = net.minecraft.resources.ResourceLocation.tryParse("cobblemon:$sanitizedName")
                        
                        if (speciesId != null && CustomFlightRegistry.hasConfig(speciesId)) {
                            val configs = CustomFlightRegistry.getConfigs(speciesId)!!
                            val config = configs.random()
                            
                            // Pokemon mới được load/phát hiện -> khởi tạo ở trạng thái đang đậu/nghỉ ngơi
                            val machine = NormalFlightStateMachine(entity, FlightState.PERCHING, config)
                            machine.markPlayerSeen()
                            activeMachines[uuid] = machine
                        }
                    }
                }
            }

            // 2. Tick các machine hiện tại và dọn dẹp machine rác
            val toRemove = mutableListOf<UUID>()
            val unloadDelay = com.toancao.pokemonai.config.FlightConfigManager.machineUnloadDelay

            for ((uuid, machine) in activeMachines) {
                if (!machine.isAlive()) {
                    machine.cleanup()
                    toRemove.add(uuid)
                } else {
                    machine.tick()
                    
                    if (machine.canBeRemovedSafely(unloadDelay)) {
                        machine.cleanup()
                        toRemove.add(uuid)
                    }
                }
            }

            toRemove.forEach { activeMachines.remove(it) }
        }
    }
}
