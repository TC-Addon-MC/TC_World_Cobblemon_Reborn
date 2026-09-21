package com.toancao.pokemonai.flight.navigation

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Chọn hành lang thoát kẹt thực sự vừa bounding box của Pokémon. */
object StuckEscape {
    data class Escape(val yaw: Double, val blockedAhead: Boolean)
    data class EscapePath(val target: Vec3, val yaw: Double, val clearance: Double)

    private data class Candidate(val yaw: Double, val dx: Double, val dy: Double, val dz: Double)

    /**
     * Quét toàn bộ bounding box dọc hành lang 8 block. Có hướng ngang, chếch
     * lên/xuống và thẳng đứng để thoát tán cây hoặc khe hẹp.
     */
    fun chooseEscapePath(mob: net.minecraft.world.entity.Mob, currentYaw: Double): EscapePath {
        val level = mob.level()
        val candidates = mutableListOf<Candidate>()
        for (k in 0 until 8) {
            val yaw = normalizeYaw(currentYaw + k * 45.0)
            val rad = Math.toRadians(yaw)
            val dx = -sin(rad)
            val dz = cos(rad)
            candidates += Candidate(yaw, dx, 0.0, dz)
            candidates += Candidate(yaw, dx, 0.5, dz)
            candidates += Candidate(yaw, dx, -0.375, dz)
        }
        candidates += Candidate(normalizeYaw(currentYaw), 0.0, 1.0, 0.0)
        candidates += Candidate(normalizeYaw(currentYaw), 0.0, -1.0, 0.0)

        var best = candidates.first()
        var bestClearance = 0.0
        var bestScore = Double.NEGATIVE_INFINITY
        for (candidate in candidates) {
            val length = sqrt(candidate.dx * candidate.dx + candidate.dy * candidate.dy + candidate.dz * candidate.dz)
            val nx = candidate.dx / length
            val ny = candidate.dy / length
            val nz = candidate.dz / length
            var clearance = 0.0
            var distance = 0.5
            while (distance <= 8.0) {
                val movedBox = mob.boundingBox.move(nx * distance, ny * distance, nz * distance)
                if (!level.noCollision(mob, movedBox)) break
                clearance = distance
                distance += 0.5
            }

            var turn = abs(candidate.yaw - currentYaw) % 360.0
            if (turn > 180.0) turn = 360.0 - turn
            val score = clearance * 100.0 + turn * 0.05 - abs(ny) * 2.0
            if (score > bestScore) {
                best = candidate
                bestClearance = clearance
                bestScore = score
            }
        }

        val length = sqrt(best.dx * best.dx + best.dy * best.dy + best.dz * best.dz)
        val travel = bestClearance
        return EscapePath(
            target = mob.position().add(
                best.dx / length * travel,
                best.dy / length * travel,
                best.dz / length * travel
            ),
            yaw = best.yaw,
            clearance = bestClearance
        )
    }

    fun chooseEscapeYaw(mob: net.minecraft.world.entity.Mob, currentYaw: Double): Escape {
        val path = chooseEscapePath(mob, currentYaw)
        return Escape(path.yaw, path.clearance < 2.0)
    }

    private fun normalizeYaw(yaw: Double): Double = ((yaw % 360.0) + 360.0) % 360.0
}
