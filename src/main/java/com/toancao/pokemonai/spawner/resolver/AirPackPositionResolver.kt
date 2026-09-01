package com.toancao.pokemonai.spawner.resolver

import com.toancao.pokemonai.spawner.FormationType
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object AirPackPositionResolver : PackPositionResolver {

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
                FormationType.V_SHAPE -> {
                    val sign = if (i % 2 == 0) 1 else -1
                    val step = (i / 2 + 1) * 3.0
                    Pair(sign * step, -step)
                }
                FormationType.COLUMN -> {
                    Pair((Random.nextDouble() - 0.5) * 2.0, -(i + 1) * 3.5)
                }
                FormationType.PROTECTIVE_CIRCLE -> {
                    val angle = (2 * Math.PI / count) * i
                    Pair(Math.cos(angle) * radius, Math.sin(angle) * radius)
                }
                FormationType.SCATTERED -> {
                    val angle = Random.nextDouble(0.0, 2 * Math.PI)
                    val dist = Random.nextDouble(2.0, radius)
                    Pair(Math.cos(angle) * dist, Math.sin(angle) * dist)
                }
            }

            val altitudeOffset = (Random.nextDouble() - 0.5) * 2.0
            result.add(Vec3(center.x + dx, center.y + altitudeOffset, center.z + dz))
        }

        return result
    }
}
