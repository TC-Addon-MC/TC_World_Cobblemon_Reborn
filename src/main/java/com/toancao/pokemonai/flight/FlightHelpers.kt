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
            // Xoay đầu mượt nhưng đủ nhanh (tăng từ 10f lên 25f) để bắt kịp vận tốc,
            // tránh hiện tượng bay ngang/lùi khi đổi hướng gắt (ví dụ lúc bay vòng).
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
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, mob.x.toInt(), mob.z.toInt()).toDouble()
    }

    fun estimateWaterSurfaceY(mob: net.minecraft.world.entity.Mob): Double {
        val level = mob.level() ?: return mob.y
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, mob.x.toInt(), mob.z.toInt()).toDouble() // Có thể không chuẩn bằng raycast nhưng an toàn hơn
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

    fun spawnTakeoffParticles(level: net.minecraft.world.level.Level, pokemon: net.minecraft.world.entity.Entity, progress: Double, style: Int = 0) {
    if (level !is net.minecraft.server.level.ServerLevel) return

    val random = pokemon.random
    val baseRadius = pokemon.bbWidth.toDouble() * 1.5

    // Giảm số lượng xuống mức cực thấp (chỉ 2 đến 4 hạt mỗi nhịp) để tránh bị đặc quánh
    val actualCount = if (style == 2) 2 else (2 + progress * 2).toInt()

    // Chuyển toàn bộ sang các hạt kích thước nhỏ, tơi xốp, dạng bụi mịn (dust)
    val (primaryParticle, secondaryParticle) = when (style) {
        0 -> Pair(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, net.minecraft.core.particles.ParticleTypes.SNOWFLAKE) // Bụi trắng li ti + sương nổi
        1 -> Pair(net.minecraft.core.particles.ParticleTypes.ASH, net.minecraft.core.particles.ParticleTypes.WHITE_ASH)       // Bụi tro đen/trắng cuốn theo gió
        2 -> Pair(net.minecraft.core.particles.ParticleTypes.GLOW, net.minecraft.core.particles.ParticleTypes.END_ROD)        // Bụi lốm đốm phát sáng
        else -> Pair(net.minecraft.core.particles.ParticleTypes.ASH, net.minecraft.core.particles.ParticleTypes.SMOKE) // Bụi đất mờ sát mặt đất
    }

    for (i in 0 until actualCount) {
        val angle = random.nextDouble() * kotlin.math.PI * 2.0
        
        // Vòng tỏa hẹp hơn để bụi ôm sát khu vực cất cánh
        val radius = baseRadius + (progress * 1.0) + (random.nextDouble() * 0.2) 

        val px = pokemon.x + kotlin.math.cos(angle) * radius
        val pz = pokemon.z + kotlin.math.sin(angle) * radius
        
        // Giảm tốc độ văng ngang, gần như không có lực đẩy dọc (vy) để bụi bay là là mặt đất
        val speed = 0.04 + random.nextDouble() * 0.04
        val vx = kotlin.math.cos(angle) * speed
        val vz = kotlin.math.sin(angle) * speed
        val vy = random.nextDouble() * 0.015 // Rất thấp

        // Bụi văng xa
        level.sendParticles(
            primaryParticle,
            px, pokemon.y + 0.05, pz,
            0, vx, vy, vz, 1.0
        )

        // Bụi lơ lửng tại chỗ (chỉ xuất hiện 33% số lần để tạo độ thưa thớt)
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
            0.3f, // Tiếng gió thật nhỏ
            1.0f + (random.nextFloat() * 0.2f)
        )
    }
}
}
