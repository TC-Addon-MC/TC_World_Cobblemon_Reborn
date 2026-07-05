package com.toancao.pokemonai.flight.spawner

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.config.FlightConfigManager
import com.toancao.pokemonai.flight.CustomFlightManager
import com.toancao.pokemonai.flight.FlightState
import com.toancao.pokemonai.registry.BlockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.random.Random

object CustomAirSpawner {
    private var tickCounter = 0
    private val dummySpawner = BasicSpawner("custom_air_spawner", CobblemonSpawnPools.WORLD_SPAWN_POOL, 32f)
    private val dummyCause = SpawnCause(dummySpawner)

    fun tick(server: MinecraftServer) {
        val interval = FlightConfigManager.airSpawnInterval
        if (interval <= 0) return

        tickCounter++
        if (tickCounter >= interval) {
            tickCounter = 0
            for (player in server.playerList.players) {
                trySpawnNearPlayer(player)
            }
        }
    }

    fun trySpawnNearPlayer(player: ServerPlayer): Boolean {
        val level = player.serverLevel()
        
        // 30% cơ hội spawn ở phía sau lưng và bay ngang qua người chơi, 70% spawn ngẫu nhiên quanh người chơi
        val isSpawnBehind = Random.nextDouble() < 0.3
        
        val targetX: Int
        val targetZ: Int
        val spawnYaw: Float

        // Lấy view distance (tính theo block) của người chơi hiện tại
        val maxViewBlocks = (player.server.playerList.viewDistance * 16).toDouble()

        if (isSpawnBehind) {
            val yaw = Math.toRadians(player.yRot.toDouble())
            val forwardX = -Math.sin(yaw)
            val forwardZ = Math.cos(yaw)
            
            // Spawn phía sau lưng người chơi (khoảng cách từ 1.0 đến tối đa tầm nhìn)
            val distance = Random.nextDouble(1.0, maxViewBlocks)
            
            // Lệch nhẹ sang 2 bên một chút
            val sideOffset = (Random.nextDouble() - 0.5) * 10.0
            val sideX = Math.cos(yaw) * sideOffset
            val sideZ = Math.sin(yaw) * sideOffset
            
            targetX = (player.x - forwardX * distance + sideX).toInt()
            targetZ = (player.z - forwardZ * distance + sideZ).toInt()
            
            // Hướng bay trùng với hướng nhìn của người chơi
            spawnYaw = player.yRot
        } else {
            // Spawn ngẫu nhiên xung quanh (khoảng cách tối đa là tầm nhìn)
            val distance = Random.nextDouble(0.0, maxViewBlocks)
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val dx = (distance * Math.cos(angle)).toInt()
            val dz = (distance * Math.sin(angle)).toInt()
            
            targetX = player.blockX + dx
            targetZ = player.blockZ + dz
            
            // Hướng bay ngẫu nhiên
            spawnYaw = Random.nextDouble(0.0, 360.0).toFloat()
        }
        
        // Tìm mặt đất để xác định độ cao
        val surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ)
        
        // Kiểm tra số lượng Pokemon trong Chunk (giới hạn pokemonPerChunk của Cobblemon)
        val chunkX = targetX shr 4
        val chunkZ = targetZ shr 4
        val minX = chunkX shl 4
        val minZ = chunkZ shl 4
        val chunkBox = net.minecraft.world.phys.AABB(minX.toDouble(), -64.0, minZ.toDouble(), (minX + 16).toDouble(), 320.0, (minZ + 16).toDouble())
        val pokemonInChunk = level.getEntitiesOfClass(PokemonEntity::class.java, chunkBox)
        // Nới lỏng giới hạn thêm 5 con để đảm bảo chim luôn có slot spawn kể cả khi dưới đất đã đầy
        if (pokemonInChunk.size >= com.cobblemon.mod.common.Cobblemon.config.pokemonPerChunk + 5) {
            return false
        }

        // === Dùng pipeline spawn thực sự của Cobblemon (như CobbleNav) ===
        // Tạo SpawningZone xung quanh người chơi và resolve ra AreaSpawnablePosition thực sự
        // (Grounded, Surface...) để isSatisfiedBy() hoạt động đúng.
        val spawnBucket = dummySpawner.chooseBucket(dummyCause, dummySpawner.influences)

        val zoneInput = com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput(
            cause = dummyCause,
            world = level,
            baseX = player.blockX - 8,
            baseY = player.blockY - 4,
            baseZ = player.blockZ - 8,
            length = 16,
            height = 8,
            width = 16
        )

        val zone = dummySpawner.generator.generate(dummySpawner, zoneInput)
        val spawnablePositions = dummySpawner.resolver.resolve(
            dummySpawner,
            com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.prioritizedAreaCalculators,
            zone
        )

        if (spawnablePositions.isEmpty()) {
            return false
        }

        // Lọc ra các spawn thỏa mãn điều kiện tại các vị trí đã resolve
        // getProbabilities tính toán chính xác rarity và xác suất của Cobblemon (giống CobbleNav)
        val spawnProbabilities = dummySpawner.selector.getProbabilities(dummySpawner, spawnBucket, spawnablePositions)
        
        // Tính tổng trọng số nguyên thủy của TẤT CẢ Pokemon (bay + không bay)
        // Việc này đảm bảo tỉ lệ xuất hiện tuyệt đối của Pokemon hiếm (Huyền thoại) không bị khuếch đại
        val totalOriginalWeight = spawnProbabilities.values.sumOf { it.toDouble() }
        if (totalOriginalWeight <= 0.0) return false

        val candidatesBySpecies = mutableMapOf<String, Pair<PokemonSpawnDetail, Float>>()
        var rejectedNotFly = 0

        for ((detail, chance) in spawnProbabilities) {
            if (detail !is PokemonSpawnDetail) continue
            val speciesName = detail.pokemon.species?.lowercase() ?: continue
            if (!FlightConfigManager.pokemonAssignments.containsKey("cobblemon:$speciesName")) {
                rejectedNotFly++
                continue
            }
            
            // Kiểm tra xem detail này có thực sự thỏa mãn vị trí nào không
            val isSatisfied = spawnablePositions.any { detail.isSatisfiedBy(it) }
            if (!isSatisfied) continue
            
            // Giữ entry có xác suất (chance) cao nhất cho mỗi loài
            val existing = candidatesBySpecies[speciesName]
            if (existing == null || chance > existing.second) {
                candidatesBySpecies[speciesName] = detail to chance
            }
        }

        val validDetails = candidatesBySpecies.values.toList()

        if (validDetails.isEmpty()) {
          
            return false
        }
     
        // Kiểm tra tỉ lệ tạo block
        val blockChance = Random.nextDouble()
        if (blockChance > FlightConfigManager.airSpawnChance) {
           return false
        }
        
        // Quay xổ số chọn 1 detail dựa vào tổng trọng số nguyên thủy
        var randomWeight = Random.nextDouble(totalOriginalWeight)
        var selectedDetail: PokemonSpawnDetail? = null
        for ((detail, chance) in validDetails) {
            randomWeight -= chance.toDouble()
            if (randomWeight <= 0) {
                selectedDetail = detail
                break
            }
        }
        
        // Nếu vòng quay rơi vào phần trọng số của Pokemon KHÔNG biết bay (randomWeight > 0)
        // -> Hủy spawn đợt này. Điều này giữ nguyên độ hiếm thật sự của game.
        if (selectedDetail == null) {
            return false
        }
        
        var speciesName = selectedDetail.pokemon.species?.lowercase() ?: return false
        
        // Bắn Event
        speciesName = com.toancao.pokemonai.api.PokemonAIEvents.ON_AIR_SPAWN.invoker().onAirSpawn(player, speciesName) ?: return false
        
        val flightConfig = FlightConfigManager.pokemonFlightConfigs["cobblemon:$speciesName"]
        if (flightConfig == null) {
          return false
        }
        
        // Độ cao bay đúng với config thay vì random quá cao
        val preferredHeight = flightConfig.preferredHeight.toInt()
        val airY = surfaceY + preferredHeight
        val airPos = BlockPos(targetX, airY, targetZ)
        
        // Đặt Cloud Block
        if (level.getBlockState(airPos).isAir) {
            level.setBlockAndUpdate(airPos, BlockRegistry.CLOUD_BLOCK.defaultBlockState())
            
            // Spawn Pokemon
            val pokemon = selectedDetail.pokemon.create()
            
            // Tính toán level hợp lý dựa trên cấp độ của Pokemon trong đội hình người chơi
            val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
            var playerMaxLevel = 1
            for (p in party) {
                if (p.level > playerMaxLevel) {
                    playerMaxLevel = p.level
                }
            }
            
            // Tìm khoảng level hợp lệ của con Pokemon này (tránh sinh ra Pidgey lv 60)
            var minLvl = 1
            var maxLvl = 100
            try {
                // Tìm property trả về IntRange trong detail
                for (field in selectedDetail.javaClass.declaredFields) {
                    if (field.type == kotlin.ranges.IntRange::class.java) {
                        field.isAccessible = true
                        val range = field.get(selectedDetail) as? kotlin.ranges.IntRange
                        if (range != null) {
                            minLvl = range.first
                            maxLvl = range.last
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            
            // Random level nhưng phải nằm trong giới hạn tự nhiên của nó
            val finalMax = Math.min(maxLvl, Math.max(minLvl, playerMaxLevel))
            val finalMin = Math.min(minLvl, finalMax)
            
            pokemon.level = kotlin.random.Random.nextInt(finalMin, finalMax + 1)
            
            val entity = PokemonEntity(level, pokemon)
            entity.setPos(airPos.x + 0.5, airPos.y + 1.0, airPos.z + 0.5)
            // Thiết lập hướng bay cho Pokemon
            entity.yRot = spawnYaw
            entity.yBodyRot = spawnYaw
            entity.yHeadRot = spawnYaw
            
            if (level.addFreshEntity(entity)) {
                // Spawn cloud: tăng stamina gấp đôi, giảm tốc độ một nửa.
                // dropOnHit = true để pokemon rơi xuống khi bị đánh thay vì kẹt lơ lửng.
                val customCloudConfig = flightConfig.copy(
                    maxFlightTicks = flightConfig.maxFlightTicks * 2,
                    flightSpeed = flightConfig.flightSpeed * 0.5,
                    dropOnHit = true
                )
                // Pokemon spawn trên không, chưa từng đứng trên mặt đất -> không được gán TAKING_OFF
                // (state đó chỉ dành cho việc cất cánh từ mặt đất). Khởi tạo thẳng vào FLYING/CIRCULAR_FLYING
                // đúng độ cao spawn; NormalFlightAI.tickFlying/tickCircularFlying tự gọi FlightEngine.flyTo
                // ngay trong tick đầu tiên nên session vẫn được khởi tạo đúng, không bị rơi về GROUNDED.
                val initialState = if (customCloudConfig.circularFlightChance > 0 &&
                    Random.nextDouble() < customCloudConfig.circularFlightChance) {
                    FlightState.CIRCULAR_FLYING
                } else {
                    FlightState.FLYING
                }
                CustomFlightManager.forceAddMachine(entity, initialState, customCloudConfig)
                return true
            } else {
                // Failed to add entity to level
            }
        } else {
            // Target pos is not air
        }
        return false
    }

    fun spawnExactPokemon(level: net.minecraft.server.level.ServerLevel, pos: BlockPos, species: String): Boolean {
        val flightConfig = FlightConfigManager.pokemonFlightConfigs["cobblemon:$species"] ?: return false
        val newSpecies = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(species) ?: return false
        val pokemon = newSpecies.create()
        
        // Cấp ngẫu nhiên
        pokemon.level = kotlin.random.Random.nextInt(20, 60)
        
        if (level.getBlockState(pos).isAir) {
            level.setBlockAndUpdate(pos, BlockRegistry.CLOUD_BLOCK.defaultBlockState())
            val entity = PokemonEntity(level, pokemon)
            entity.setPos(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
            
            if (level.addFreshEntity(entity)) {
                val customCloudConfig = flightConfig.copy(
                    maxFlightTicks = flightConfig.maxFlightTicks * 2,
                    flightSpeed = flightConfig.flightSpeed * 0.5,
                    dropOnHit = true
                )
                val initialState = FlightState.FLYING
                CustomFlightManager.forceAddMachine(entity, initialState, customCloudConfig)
                return true
            }
        }
        return false
    }
}