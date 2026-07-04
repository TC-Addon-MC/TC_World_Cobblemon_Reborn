package com.toancao.pokemonai.flight

import com.toancao.pokemonai.flight.CustomFlightProfile
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

object FlightHelpers {
    fun applyDirectionChange(profile: CustomFlightProfile) {
        val nudge = (kotlin.random.Random.nextDouble() - 0.5) * 60.0
        profile.currentYaw = (profile.currentYaw + nudge) % 360.0
        if (profile.currentYaw < 0) profile.currentYaw += 360.0
    }
    
    fun computeHeightCorrection(mob: net.minecraft.world.entity.Mob, profile: CustomFlightProfile): Double {
        val groundY = estimateGroundY(mob)
        val currentHeight = mob.y - groundY
        val diff = profile.currentPreferredHeight - currentHeight
        // Trả về lực đẩy y tỷ lệ với độ lệch độ cao
        return diff * 0.05
    }

    fun syncRotationFromVelocity(mob: net.minecraft.world.entity.Mob) {
        val vel = mob.deltaMovement
        if (vel.x * vel.x + vel.z * vel.z > 0.001) {
            val yaw = (Math.toDegrees(atan2(vel.z, vel.x)) - 90.0).toFloat()
            // Xoay mượt
            mob.yRot = rotlerp(mob.yRot, yaw, 10f)
            mob.yBodyRot = mob.yRot
            mob.yHeadRot = mob.yRot
        }
    }
    
    fun rotlerp(current: Float, target: Float, maxChange: Float): Float {
        var diff = net.minecraft.util.Mth.wrapDegrees(target - current)
        if (diff > maxChange) diff = maxChange
        if (diff < -maxChange) diff = -maxChange
        return current + diff
    }

    fun estimateGroundY(mob: net.minecraft.world.entity.Mob): Double {
        val level = mob.level() ?: return mob.y - 10.0
        val groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, mob.x.toInt(), mob.z.toInt())
        return groundY.toDouble()
    }

    fun estimateWaterSurfaceY(mob: net.minecraft.world.entity.Mob): Double {
        val level = mob.level() ?: return mob.y - 10.0
        val surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, mob.x.toInt(), mob.z.toInt())
        return surfaceY.toDouble()
    }

    /**
     * Trả về khoảng cách đến player gần nhất trong vòng activationRadius.
     * Trả về -1.0 nếu không tìm thấy.
     */
    fun nearestPlayerDistance(mob: net.minecraft.world.entity.Mob): Double {
        val level = mob.level() ?: return -1.0
        val r = 128.0 // Scan rộng hơn để giảm tốc độ từ từ khi ra xa
        val players = level.getEntitiesOfClass(
            net.minecraft.world.entity.player.Player::class.java,
            net.minecraft.world.phys.AABB(mob.x - r, mob.y - r, mob.z - r, mob.x + r, mob.y + r, mob.z + r)
        )
        return if (players.isEmpty()) -1.0
        else players.minOf { it.distanceTo(mob).toDouble() }
    }

    fun disableAI(mob: net.minecraft.world.entity.Mob) {
        mob.navigation?.stop()
    }

    fun restoreAI(mob: net.minecraft.world.entity.Mob) {
        // AI sẽ tự động điều khiển lại bình thường khi trạng thái bay kết thúc
    }

    // ── Shared Logic (Deduplication) ──────────────────────────────────────────

    enum class WaterStatus { NONE, SURFACE, SUBMERGED }

    fun checkWaterStatus(mob: net.minecraft.world.entity.Mob): WaterStatus {
        if (mob.isUnderWater) return WaterStatus.SUBMERGED
        if (mob.isInWater) return WaterStatus.SURFACE
        return WaterStatus.NONE
    }

    fun applyFlyingPhysics(pokemon: com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        mob.isNoGravity = true
        disableAI(mob)
        com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
    }

    fun terminateFlight(pokemon: com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        mob.isNoGravity = false
        restoreAI(mob)
        com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
    }

    fun checkObstacleAhead(
        mob: net.minecraft.world.entity.Mob,
        rawVec: Vec3,
        checkRange: Int,
        climbAmount: Double
    ): Vec3 {
        val level = mob.level() ?: return rawVec
        val norm = rawVec.normalize()
        var blockAhead = false

        for (i in 1..checkRange) {
            val checkX = mob.x + norm.x * i
            val checkZ = mob.z + norm.z * i
            val checkPos = net.minecraft.core.BlockPos(checkX.toInt(), mob.y.toInt(), checkZ.toInt())
            if (!level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty) {
                blockAhead = true
                break
            }
        }

        return if (blockAhead) Vec3(rawVec.x, rawVec.y + climbAmount * 10.0, rawVec.z)
        else rawVec
    }
}
