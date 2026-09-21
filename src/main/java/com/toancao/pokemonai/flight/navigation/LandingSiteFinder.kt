package com.toancao.pokemonai.flight.navigation

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.phys.Vec3

/**
 * Phase 2: chọn điểm đáp an toàn, kiểm tra bounding box thật.
 * Chỉ dùng chunk đã load, không ép load chunk, không sửa world.
 */
object LandingSiteFinder {
    fun findLandingSite(
        pokemon: PokemonEntity,
        forwardDistance: Int = 12,
        searchRadius: Int = 6
    ): Vec3? {
        val mob = pokemon as net.minecraft.world.entity.Mob
        val level = mob.level() ?: return null
        if (level.isClientSide) return null

        val yaw = Math.toRadians(mob.yRot.toDouble())
        val dirX = -kotlin.math.sin(yaw)
        val dirZ = kotlin.math.cos(yaw)

        val centerX = mob.x + dirX * forwardDistance
        val centerZ = mob.z + dirZ * forwardDistance

        var best: Vec3? = null
        var bestScore = Double.MAX_VALUE

        for (dx in -searchRadius..searchRadius) {
            for (dz in -searchRadius..searchRadius) {
                val x = (centerX + dx).toInt()
                val z = (centerZ + dz).toInt()
                if (!level.hasChunk(x shr 4, z shr 4)) continue
                if (!level.worldBorder.isWithinBounds(BlockPos(x, mob.blockY, z))) continue

                val topY = try {
                    level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x, z
                    )
                } catch (_: Exception) {
                    level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        x, z
                    )
                }
                if (topY < level.minBuildHeight || topY > level.maxBuildHeight) continue

                val groundPos = BlockPos(x, topY - 1, z)
                val groundState = level.getBlockState(groundPos)
                if (groundState.getCollisionShape(level, groundPos).isEmpty) continue
                if (!level.getFluidState(groundPos).isEmpty) continue
                if (groundState.`is`(BlockTags.LEAVES)) continue

                val target = Vec3(x + 0.5, topY.toDouble(), z + 0.5)
                val movedBox = mob.boundingBox.move(
                    target.x - mob.x,
                    target.y - mob.y,
                    target.z - mob.z
                )
                if (!level.noCollision(mob, movedBox)) continue
                if (!level.getFluidState(BlockPos.containing(target)).isEmpty) continue

                val dist = kotlin.math.sqrt(dx * dx + dz * dz.toDouble())
                val heightDiff = kotlin.math.abs(topY - mob.y)
                val score = dist * 1.0 + heightDiff * 0.5
                if (score < bestScore) {
                    bestScore = score
                    best = target
                }
            }
        }
        return best
    }

    fun canLand(pokemon: PokemonEntity): Boolean {
        return try {
            pokemon.canWalk()
        } catch (_: Exception) {
            true
        }
    }
}
