package com.toancao.pokemonai.spawner.resolver

import com.toancao.pokemonai.spawner.FormationType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object LandPackPositionResolver : PackPositionResolver {

    override fun resolvePositions(
        level: ServerLevel,
        center: Vec3,
        count: Int,
        radius: Double,
        formation: FormationType
    ): List<Vec3> {
        val result = mutableListOf<Vec3>()

        for (i in 0 until count) {
            val (dx, dz) = when (formation) {
                FormationType.SCATTERED -> {
                    val angle = Random.nextDouble(0.0, 2 * Math.PI)
                    val dist = Random.nextDouble(2.0, radius)
                    Pair(Math.cos(angle) * dist, Math.sin(angle) * dist)
                }
                FormationType.PROTECTIVE_CIRCLE -> {
                    val angle = (2 * Math.PI / count) * i
                    Pair(Math.cos(angle) * radius, Math.sin(angle) * radius)
                }
                FormationType.COLUMN -> {
                    val angle = 0.0 // Theo trục Z
                    Pair((Random.nextDouble() - 0.5) * 2.0, -(i + 1) * 2.5)
                }
                FormationType.V_SHAPE -> {
                    val sign = if (i % 2 == 0) 1 else -1
                    val step = (i / 2 + 1) * 2.5
                    Pair(sign * step, -step)
                }
            }

            val targetX = (center.x + dx).toInt()
            val targetZ = (center.z + dz).toInt()
            val surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ)

            // Đảm bảo không rơi vào khoảng không vô tận
            val validY = if (surfaceY > level.minBuildHeight) surfaceY.toDouble() else center.y
            result.add(Vec3(targetX + 0.5, validY, targetZ + 0.5))
        }

        return result
    }
}
