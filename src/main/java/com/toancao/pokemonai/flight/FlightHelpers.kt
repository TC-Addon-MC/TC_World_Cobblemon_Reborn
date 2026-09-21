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
        return diff * 0.05
    }

    fun syncRotationFromVelocity(mob: net.minecraft.world.entity.Mob) {
        val vel = mob.deltaMovement
        if (vel.x * vel.x + vel.z * vel.z > 0.001) {
            val yaw = (Math.toDegrees(atan2(vel.z, vel.x)) - 90.0).toFloat()
            mob.yRot = rotlerp(mob.yRot, yaw, 25f)
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
        val level = mob.level() ?: return mob.y
        return try {
            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mob.x.toInt(), mob.z.toInt()).toDouble()
        } catch (_: Exception) {
            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, mob.x.toInt(), mob.z.toInt()).toDouble()
        }
    }

    /**
     * Phase 1: resolve mặt nước thật bằng fluid scan, không dùng OCEAN_FLOOR (đáy biển).
     * Trả null khi không có cột nước liên tục quanh entity.
     */
    fun findWaterSurfaceY(level: net.minecraft.world.level.Level, x: Int, startY: Int, z: Int): Double? {
        val maxY = level.maxBuildHeight
        val minY = level.minBuildHeight
        var baseY = startY.coerceIn(minY, maxY)

        var waterY: Int? = null
        if (level.getFluidState(net.minecraft.core.BlockPos(x, baseY, z)).`is`(net.minecraft.tags.FluidTags.WATER)) {
            waterY = baseY
        } else {
            for (dy in -2..2) {
                val y = baseY + dy
                if (y < minY || y > maxY) continue
                if (level.getFluidState(net.minecraft.core.BlockPos(x, y, z)).`is`(net.minecraft.tags.FluidTags.WATER)) {
                    waterY = y
                    break
                }
            }
        }
        if (waterY == null) return null

        var topY = waterY
        while (topY + 1 <= maxY) {
            val next = net.minecraft.core.BlockPos(x, topY + 1, z)
            if (level.getFluidState(next).`is`(net.minecraft.tags.FluidTags.WATER)) {
                topY++
            } else break
        }
        val topPos = net.minecraft.core.BlockPos(x, topY, z)
        val fluid = level.getFluidState(topPos)
        if (!fluid.`is`(net.minecraft.tags.FluidTags.WATER)) return null
        return topPos.y + fluid.getHeight(level, topPos).toDouble()
    }

    fun findWaterSurfaceY(mob: net.minecraft.world.entity.Mob): Double? {
        val level = mob.level() ?: return null
        val startY = kotlin.math.floor(mob.boundingBox.minY).toInt()
        return findWaterSurfaceY(level, mob.blockX, startY, mob.blockZ)
    }

    @Deprecated("Dùng findWaterSurfaceY để tránh OCEAN_FLOOR trả về đáy biển", ReplaceWith("findWaterSurfaceY(mob) ?: mob.y"))
    fun estimateWaterSurfaceY(mob: net.minecraft.world.entity.Mob): Double {
        return findWaterSurfaceY(mob) ?: mob.y
    }


    fun disableAI(mob: net.minecraft.world.entity.Mob) {
        mob.navigation?.stop()
    }

    fun restoreAI(mob: net.minecraft.world.entity.Mob) {
    }


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
        try {
            mob.navigation?.stop()
        } catch (_: Exception) {
        }
        restoreAI(mob)
        com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
    }

    /**
     * Đợt 3: né vật cản 3 tầng (chân + thân + đầu), có kiểm tra trần.
     * Thay cú vọt `climbAmount * 10` bằng lực nâng vừa phải, tránh vọt độ cao đột ngột.
     */
    fun checkObstacleAhead(
        mob: net.minecraft.world.entity.Mob,
        rawVec: Vec3,
        checkRange: Int,
        climbAmount: Double
    ): Vec3 {
        val level = mob.level() ?: return rawVec
        val len = rawVec.length()
        if (len < 1e-6) return rawVec
        val norm = rawVec.normalize()
        val baseY = mob.y.toInt()
        var blockedLevel = -1

        outer@ for (i in 1..checkRange.coerceIn(1, 8)) {
            val checkX = (mob.x + norm.x * i).toInt()
            val checkZ = (mob.z + norm.z * i).toInt()
            for (dy in 0..2) {
                val pos = net.minecraft.core.BlockPos(checkX, baseY + dy, checkZ)
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty) {
                    blockedLevel = dy
                    break@outer
                }
            }
        }
        if (blockedLevel < 0) return rawVec

        val headroomBlocked = (1..2).any { dy ->
            val pos = net.minecraft.core.BlockPos(mob.blockX, baseY + 3 + dy, mob.blockZ)
            !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
        }
        if (headroomBlocked) {
            return Vec3(rawVec.x * 0.3, rawVec.y.coerceAtMost(0.0), rawVec.z * 0.3)
        }

        val lift = climbAmount.coerceIn(0.0, 3.0) * (1.0 - blockedLevel * 0.25).coerceAtLeast(0.25)
        return Vec3(rawVec.x, rawVec.y + lift, rawVec.z)
    }

    fun spawnTakeoffParticles(level: net.minecraft.world.level.Level, pokemon: net.minecraft.world.entity.Entity, progress: Double, style: Int = 0) {
    if (level !is net.minecraft.server.level.ServerLevel) return

    val random = pokemon.random
    val baseRadius = pokemon.bbWidth.toDouble() * 1.5

    val actualCount = if (style == 2) 2 else (2 + progress * 2).toInt()

    val (primaryParticle, secondaryParticle) = when (style) {
        0 -> Pair(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, net.minecraft.core.particles.ParticleTypes.SNOWFLAKE)
        1 -> Pair(net.minecraft.core.particles.ParticleTypes.ASH, net.minecraft.core.particles.ParticleTypes.WHITE_ASH)
        2 -> Pair(net.minecraft.core.particles.ParticleTypes.GLOW, net.minecraft.core.particles.ParticleTypes.END_ROD)
        else -> Pair(net.minecraft.core.particles.ParticleTypes.ASH, net.minecraft.core.particles.ParticleTypes.SMOKE)
    }

    for (i in 0 until actualCount) {
        val angle = random.nextDouble() * kotlin.math.PI * 2.0
        
        val radius = baseRadius + (progress * 1.0) + (random.nextDouble() * 0.2) 

        val px = pokemon.x + kotlin.math.cos(angle) * radius
        val pz = pokemon.z + kotlin.math.sin(angle) * radius
        
        val speed = 0.04 + random.nextDouble() * 0.04
        val vx = kotlin.math.cos(angle) * speed
        val vz = kotlin.math.sin(angle) * speed
        val vy = random.nextDouble() * 0.015

        level.sendParticles(
            primaryParticle,
            px, pokemon.y + 0.05, pz,
            0, vx, vy, vz, 1.0
        )

        if (random.nextInt(3) == 0) {
            level.sendParticles(
                secondaryParticle,
                px + (random.nextDouble() - 0.5) * 0.2,
                pokemon.y + 0.1 + random.nextDouble() * 0.1,
                pz + (random.nextDouble() - 0.5) * 0.2,
                0, vx * 0.1, vy * 0.5, vz * 0.1, 1.0
            )
        }
    }

    if (progress < 0.1) {
        level.playSound(
            null,
            pokemon.x, pokemon.y, pokemon.z,
            net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST,
            net.minecraft.sounds.SoundSource.AMBIENT,
            0.3f,
            1.0f + (random.nextFloat() * 0.2f)
        )
    }
}
}
