package com.toancao.pokemonai.flight.spawner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator
import com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.api.PokemonAIEvents
import com.toancao.pokemonai.config.FlightConfigManager
import com.toancao.pokemonai.flight.CustomFlightManager
import com.toancao.pokemonai.flight.FlightState
import com.toancao.pokemonai.registry.BlockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

object CustomAirSpawner {
    private var tickCounter = 0
    private val airSpawner by lazy {
        BasicSpawner(
            "custom_air_spawner",
            CobblemonSpawnPools.WORLD_SPAWN_POOL,
            32f,
            Cobblemon.bestSpawner.config.worldBuckets
        )
    }

    fun tick(server: MinecraftServer) {
        val interval = FlightConfigManager.airSpawnInterval
        if (interval <= 0 || ++tickCounter < interval) return

        tickCounter = 0
        server.playerList.players.forEach(::trySpawnNearPlayer)
    }

    fun trySpawnNearPlayer(player: ServerPlayer): Boolean {
        val level = player.serverLevel()
        val random = level.random
        val radius = FlightConfigManager.airSpawnRadius.coerceAtLeast(1.0)
        val behind = random.nextDouble() < 0.3
        val yaw = Math.toRadians(player.yRot.toDouble())
        val distance = random.nextDouble() * radius
        val angle = random.nextDouble() * Math.PI * 2.0
        val sideOffset = (random.nextDouble() - 0.5) * 10.0

        val targetX: Int
        val targetZ: Int
        val spawnYaw: Float
        if (behind) {
            targetX = (player.x + sin(yaw) * distance + cos(yaw) * sideOffset).toInt()
            targetZ = (player.z - cos(yaw) * distance + sin(yaw) * sideOffset).toInt()
            spawnYaw = player.yRot
        } else {
            targetX = (player.x + cos(angle) * distance).toInt()
            targetZ = (player.z + sin(angle) * distance).toInt()
            spawnYaw = (random.nextDouble() * 360.0).toFloat()
        }

        if (!level.hasChunk(targetX shr 4, targetZ shr 4)) return false
        if (countPokemonInChunk(level, targetX shr 4, targetZ shr 4) >= Cobblemon.config.pokemonPerChunk) return false
        if (random.nextDouble() > FlightConfigManager.airSpawnChance.coerceIn(0.0, 1.0)) return false

        val cause = SpawnCause(airSpawner, player)
        val bucket = airSpawner.chooseBucket(cause, airSpawner.influences)
        val zoneInput = SpawningZoneInput(
            cause = cause,
            world = level,
            baseX = targetX - 8,
            baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ) - 4,
            baseZ = targetZ - 8,
            length = 16,
            height = 8,
            width = 16
        )
        val zone = airSpawner.generator.generate(airSpawner, zoneInput)
        val positions = airSpawner.resolver.resolve(
            airSpawner,
            SpawnablePositionCalculator.prioritizedAreaCalculators,
            zone
        )
        if (positions.isEmpty()) return false

        val probabilities = airSpawner.selector.getProbabilities(airSpawner, bucket, positions)
        val totalWeight = probabilities.values.sumOf { it.toDouble() }
        if (totalWeight <= 0.0) return false

        var roll = random.nextDouble() * totalWeight
        var selected: PokemonSpawnDetail? = null
        for ((detail, weight) in probabilities) {
            roll -= weight.toDouble()
            if (roll <= 0.0) {
                selected = detail as? PokemonSpawnDetail
                break
            }
        }
        val detail = selected ?: return false
        val originalSpecies = detail.pokemon.species?.lowercase() ?: return false
        if (!FlightConfigManager.pokemonAssignments.containsKey("cobblemon:$originalSpecies")) return false

        val species = PokemonAIEvents.ON_AIR_SPAWN.invoker().onAirSpawn(player, originalSpecies) ?: return false
        val flightConfig = FlightConfigManager.pokemonFlightConfigs["cobblemon:$species"] ?: return false
        val spawnablePosition = positions.filter { detail.isSatisfiedBy(it) }.randomOrNull(random) ?: return false
        val airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ) + flightConfig.preferredHeight.toInt()
        val airPos = BlockPos(targetX, airY, targetZ)
        if (!isSafeAirPosition(level, airPos)) return false

        val action = PokemonSpawnAction(
            spawnablePosition,
            bucket,
            detail,
            if (species == originalSpecies) detail.pokemon else PokemonProperties.parse("species=$species"),
            selectHeldItem(detail, spawnablePosition, random.nextDouble() * 100.0),
            detail.drops,
            detail.pokemon.deriveLevelRange(detail.levelRange)
        )
        val result = action.complete() ?: return false
        val entity = result.entities.filterIsInstance<PokemonEntity>().firstOrNull() ?: return false
        entity.setPos(airPos.x + 0.5, airPos.y + 1.5, airPos.z + 0.5)
        entity.yRot = spawnYaw
        entity.yBodyRot = spawnYaw
        entity.yHeadRot = spawnYaw
        val feet = net.minecraft.world.phys.Vec3(airPos.x + 0.5, airPos.y + 1.5, airPos.z + 0.5)
        val movedBox = entity.boundingBox.move(feet.x - entity.x, feet.y - entity.y, feet.z - entity.z)
        if (!level.noCollision(entity, movedBox) || !level.getFluidState(BlockPos.containing(feet)).isEmpty) {
            entity.discard()
            return false
        }
        entity.isNoGravity = true
        level.setBlockAndUpdate(airPos, BlockRegistry.CLOUD_BLOCK.defaultBlockState())
        startAirFlight(entity, flightConfig)
        if (CustomFlightManager.getMachine(entity.uuid) == null) {
            entity.isNoGravity = false
        }
        return true
    }

    fun spawnExactPokemon(level: ServerLevel, pos: BlockPos, species: String): Boolean {
        val flightConfig = FlightConfigManager.pokemonFlightConfigs["cobblemon:$species"] ?: return false
        val pokemonSpecies = PokemonSpecies.getByName(species) ?: return false
        val surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.x, pos.z)
        val airPos = BlockPos(pos.x, surfaceY + flightConfig.preferredHeight.toInt(), pos.z)
        if (!level.hasChunk(airPos.x shr 4, airPos.z shr 4) || !isSafeAirPosition(level, airPos)) return false

        val entity = pokemonSpecies.create()
            .apply { this.level = level.random.nextInt(40) + 20 }
            .sendOut(level, airPos.center, null) {}
            ?: return false
        entity.setPos(airPos.x + 0.5, airPos.y + 1.5, airPos.z + 0.5)
        val feet = net.minecraft.world.phys.Vec3(airPos.x + 0.5, airPos.y + 1.5, airPos.z + 0.5)
        val movedBox = entity.boundingBox.move(feet.x - entity.x, feet.y - entity.y, feet.z - entity.z)
        if (!level.noCollision(entity, movedBox) || !level.getFluidState(BlockPos.containing(feet)).isEmpty) {
            entity.discard()
            return false
        }
        entity.isNoGravity = true
        level.setBlockAndUpdate(airPos, BlockRegistry.CLOUD_BLOCK.defaultBlockState())
        startAirFlight(entity, flightConfig)
        if (CustomFlightManager.getMachine(entity.uuid) == null) {
            entity.isNoGravity = false
        }
        return true
    }

    private fun startAirFlight(entity: PokemonEntity, config: com.toancao.pokemonai.flight.FlightConfig) {
        val airConfig = config.copy(
            maxFlightTicks = config.maxFlightTicks * 2,
            flightSpeed = config.flightSpeed * 0.5,
            dropOnHit = true
        )
        val initialState = if (airConfig.circularFlightChance > 0 && entity.random.nextDouble() < airConfig.circularFlightChance) {
            FlightState.CIRCULAR_FLYING
        } else {
            FlightState.FLYING
        }
        CustomFlightManager.forceAddMachine(entity, initialState, airConfig)
    }

    private fun selectHeldItem(
        detail: PokemonSpawnDetail,
        position: SpawnablePosition,
        roll: Double
    ): ItemStack {
        var remaining = roll
        for (possible in detail.heldItems ?: emptyList()) {
            remaining -= possible.percentage
            if (remaining <= 0.0) return possible.createStack(position) ?: ItemStack.EMPTY
        }
        return ItemStack.EMPTY
    }

    /**
     * Đợt 3: kiểm tra cả khối 3x3x3 quanh điểm spawn thay vì 2 block,
     * tránh spawn loài lớn kẹt nửa thân trong tường/cây.
     */
    private fun isSafeAirPosition(level: ServerLevel, pos: BlockPos): Boolean {
        for (dx in -1..1) {
            for (dy in 0..2) {
                for (dz in -1..1) {
                    val p = pos.offset(dx, dy, dz)
                    if (!level.getBlockState(p).isAir) return false
                    if (!level.getFluidState(p).isEmpty) return false
                }
            }
        }
        return true
    }

    private fun countPokemonInChunk(level: ServerLevel, chunkX: Int, chunkZ: Int): Int {
        val minX = (chunkX shl 4).toDouble()
        val minZ = (chunkZ shl 4).toDouble()
        return level.getEntitiesOfClass(
            PokemonEntity::class.java,
            AABB(minX, level.minBuildHeight.toDouble(), minZ, minX + 16.0, level.maxBuildHeight.toDouble(), minZ + 16.0)
        ).size
    }

    private fun <T> List<T>.randomOrNull(random: net.minecraft.util.RandomSource): T? {
        return if (isEmpty()) null else this[random.nextInt(size)]
    }
}
