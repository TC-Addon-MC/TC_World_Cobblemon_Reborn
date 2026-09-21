package com.toancao.pokemonai.spawner.resolver

import com.toancao.pokemonai.spawner.FormationType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object WaterPackPositionResolver : PackPositionResolver {

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
                FormationType.COLUMN -> {
                    Pair((Random.nextDouble() - 0.5) * 1.5, -(i + 1) * 2.0)
                }
                FormationType.PROTECTIVE_CIRCLE -> {
                    val angle = (2 * Math.PI / count) * i
                    Pair(Math.cos(angle) * radius, Math.sin(angle) * radius)
                }
                FormationType.V_SHAPE -> {
                    val sign = if (i % 2 == 0) 1 else -1
                    val step = (i / 2 + 1) * 2.0
                    Pair(sign * step, -step)
                }
                FormationType.SCATTERED -> {
                    val angle = Random.nextDouble(0.0, 2 * Math.PI)
                    val dist = Random.nextDouble(1.5, radius)
                    Pair(Math.cos(angle) * dist, Math.sin(angle) * dist)
                }
            }

            val targetX = (center.x + dx).toInt()
            val targetZ = (center.z + dz).toInt()
            val targetY = center.y.toInt()

            var validY = center.y
            for (dy in -3..3) {
                val checkPos = BlockPos(targetX, targetY + dy, targetZ)
                if (level.getFluidState(checkPos).`is`(FluidTags.WATER)) {
                    validY = (targetY + dy).toDouble() + 0.5
                    break
                }
            }

            result.add(Vec3(targetX + 0.5, validY, targetZ + 0.5))
        }

        return result
    }
}
