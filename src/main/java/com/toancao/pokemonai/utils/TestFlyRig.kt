package com.toancao.pokemonai.utils

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.CustomFlightManager
import com.toancao.pokemonai.flight.FlightState
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap

/**
 * Sân bay thử nghiệm cho hệ thống bay: dựng trên mặt đất quanh người chơi,
 * không đào đất (mọi cấu trúc nằm trên terrain), snapshot để `/tcpoke testfly clear`
 * khôi phục lại. Chỉ dùng debug, không dùng gameplay thật.
 */
object TestFlyRig {
    const val TEST_TAG = "tc_testfly"

    private var rigLevel: ServerLevel? = null
    private var rigCenter: BlockPos? = null
    private val snapshot = LinkedHashMap<BlockPos, BlockState>()

    data class BuildReport(val center: BlockPos, val blocksPlaced: Int, val birdSpawned: String?)

    fun build(level: ServerLevel, player: ServerPlayer, species: String): BuildReport {
        clear(level, silent = true)

        val yaw = Math.toRadians(player.yRot.toDouble())
        val fx = -kotlin.math.sin(yaw)
        val fz = kotlin.math.cos(yaw)
        val rx = -fz
        val rz = fx

        fun point(d: Double, r: Double): BlockPos {
            val x = (player.x + fx * d + rx * r).toInt()
            val z = (player.z + fz * d + rz * r).toInt()
            if (!level.hasChunk(x shr 4, z shr 4)) throw IllegalStateException("Chunk chưa load tại ($x, $z)")
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            return BlockPos(x, y, z)
        }

        var placed = 0
        fun put(pos: BlockPos, state: BlockState) {
            if (!snapshot.containsKey(pos)) snapshot[pos] = level.getBlockState(pos)
            level.setBlockAndUpdate(pos, state)
            placed++
        }
        fun fill(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, state: BlockState) {
            for (x in minOf(x0, x1)..maxOf(x0, x1)) {
                for (y in minOf(y0, y1)..maxOf(y0, y1)) {
                    for (z in minOf(z0, z1)..maxOf(z0, z1)) {
                        put(BlockPos(x, y, z), state)
                    }
                }
            }
        }

        val pad = point(40.0, 0.0)
        fill(pad.x - 7, pad.y, pad.z - 7, pad.x + 7, pad.y, pad.z + 7, Blocks.OAK_PLANKS.defaultBlockState())

        val wall = point(65.0, 0.0)
        if (kotlin.math.abs(fx) >= kotlin.math.abs(fz)) {
            fill(wall.x, wall.y, wall.z - 5, wall.x, wall.y + 7, wall.z + 5, Blocks.STONE_BRICKS.defaultBlockState())
        } else {
            fill(wall.x - 5, wall.y, wall.z, wall.x + 5, wall.y + 7, wall.z, Blocks.STONE_BRICKS.defaultBlockState())
        }

        val canopy = point(78.0, 0.0)
        fill(canopy.x - 6, canopy.y + 9, canopy.z - 6, canopy.x + 6, canopy.y + 9, canopy.z + 6, Blocks.OAK_LEAVES.defaultBlockState())

        val pond = point(95.0, 0.0)
        for (dx in -7..7) {
            for (dz in -7..7) {
                val edge = kotlin.math.abs(dx) == 7 || kotlin.math.abs(dz) == 7
                if (edge) {
                    fill(pond.x + dx, pond.y, pond.z + dz, pond.x + dx, pond.y + 3, pond.z + dz, Blocks.STONE_BRICKS.defaultBlockState())
                }
            }
        }
        fill(pond.x - 6, pond.y + 1, pond.z - 6, pond.x + 6, pond.y + 3, pond.z + 6, Blocks.WATER.defaultBlockState())

        val roof = point(65.0, 25.0)
        for ((ox, oz) in listOf(-5 to -5, -5 to 5, 5 to -5, 5 to 5)) {
            fill(roof.x + ox, roof.y, roof.z + oz, roof.x + ox, roof.y + 7, roof.z + oz, Blocks.COBBLESTONE.defaultBlockState())
        }
        fill(roof.x - 5, roof.y + 8, roof.z - 5, roof.x + 5, roof.y + 8, roof.z + 5, Blocks.OAK_PLANKS.defaultBlockState())

        val deep = point(95.0, 25.0)
        for (dx in -4..4) {
            for (dz in -4..4) {
                val edge = kotlin.math.abs(dx) == 4 || kotlin.math.abs(dz) == 4
                if (edge) {
                    fill(deep.x + dx, deep.y, deep.z + dz, deep.x + dx, deep.y + 13, deep.z + dz, Blocks.GLASS.defaultBlockState())
                }
            }
        }
        fill(deep.x - 3, deep.y + 1, deep.z - 3, deep.x + 3, deep.y + 12, deep.z + 3, Blocks.WATER.defaultBlockState())

        rigLevel = level
        rigCenter = pad

        val birdName = spawnTestBird(level, pad, species)

        return BuildReport(pad, placed, birdName)
    }

    private fun spawnTestBird(level: ServerLevel, pad: BlockPos, species: String): String? {
        if (PokemonSpecies.getByName(species.lowercase()) == null) return null
        if (!com.toancao.pokemonai.flight.CustomFlightRegistry.hasConfig(
                net.minecraft.resources.ResourceLocation.tryParse("cobblemon:${species.lowercase()}") ?: return null
            )
        ) return null
        return try {
            val pokemon = PokemonProperties.parse("species=${species.lowercase()} level=20").create()
            val entity = PokemonEntity(level, pokemon)
            entity.setPos(pad.x + 0.5, pad.y + 2.0, pad.z + 0.5)
            entity.yRot = 0f
            if (!level.addFreshEntity(entity)) return null
            entity.addTag(TEST_TAG)
            com.toancao.pokemonai.behaviors.BehaviorApplicator.apply(entity)
            CustomFlightManager.forceAddMachine(entity, FlightState.PERCHING)
            species.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /** Khôi phục block + dọn chim test. Trả về số block đã khôi phục. */
    fun clear(level: ServerLevel, silent: Boolean = false): Int {
        var restored = 0
        val targetLevel = rigLevel ?: level
        for ((pos, state) in snapshot) {
            try {
                targetLevel.setBlockAndUpdate(pos, state)
                restored++
            } catch (_: Exception) {
            }
        }
        snapshot.clear()
        val center = rigCenter
        if (center != null) {
            val lvl = targetLevel ?: level
            try {
                val box = net.minecraft.world.phys.AABB(
                    center.x - 150.0, (lvl.minBuildHeight).toDouble(), center.z - 150.0,
                    center.x + 150.0, (lvl.maxBuildHeight).toDouble(), center.z + 150.0
                )
                for (e in lvl.getEntitiesOfClass(PokemonEntity::class.java, box) { it.tags.contains(TEST_TAG) }) {
                    e.discard()
                }
            } catch (_: Exception) {
            }
        }
        rigLevel = null
        rigCenter = null
        if (!silent) {
        }
        return restored
    }
}
