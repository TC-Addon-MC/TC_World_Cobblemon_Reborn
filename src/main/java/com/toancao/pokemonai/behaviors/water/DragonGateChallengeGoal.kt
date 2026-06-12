package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.particles.ParticleTypes

class DragonGateChallengeGoal(private val entity: PokemonEntity) : Goal() {
    private var targetPos: BlockPos? = null

    init {
        // High priority? In MagikarpConfig it's priority 5, wait, lower number = higher priority in MC? No, we might want it to override normal swimming.
    }

    override fun canUse(): Boolean {
        val living = entity as LivingEntity
        if (!living.tags.contains("dragon_gate_challenger")) return false
        
        // Find target tags
        var tx: Int? = null
        var ty: Int? = null
        var tz: Int? = null
        
        for (tag in living.tags) {
            if (tag.startsWith("target_x_")) tx = tag.substringAfter("target_x_").toIntOrNull()
            if (tag.startsWith("target_y_")) ty = tag.substringAfter("target_y_").toIntOrNull()
            if (tag.startsWith("target_z_")) tz = tag.substringAfter("target_z_").toIntOrNull()
        }
        
        if (tx != null && ty != null && tz != null) {
            targetPos = BlockPos(tx, ty, tz)
            return true
        }
        return false
    }

    override fun canContinueToUse(): Boolean {
        return canUse()
    }

    override fun tick() {
        val target = targetPos ?: return
        val living = entity as LivingEntity
        val level = living.level() as? ServerLevel ?: return
        val distSqr = living.distanceToSqr(target.x.toDouble(), target.y.toDouble(), target.z.toDouble())

        if (distSqr > 25.0) { // Cần tiếp tục bơi
            val dir = Vec3(
                target.x.toDouble() - living.x,
                target.y.toDouble() - living.y,
                target.z.toDouble() - living.z
            ).normalize()
            
            // Bơi mạnh lên thác
            val currentVel = living.deltaMovement
            val speed = 0.3
            living.deltaMovement = Vec3(
                currentVel.x + dir.x * speed,
                currentVel.y + dir.y * speed + 0.1, // Luôn cố trồi lên mặt nước hoặc leo thác
                currentVel.z + dir.z * speed
            )
            
            // Xoay mặt về hướng target
            living.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3(target.x.toDouble(), target.y.toDouble(), target.z.toDouble()))
            living.yBodyRot = living.yRotO

            // Sinh particle bọt nước khi bơi mạnh
            if (level.random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.SPLASH, living.x, living.y, living.z, 5, 0.5, 0.5, 0.5, 0.1)
            }
        } else {
            // Đã đến đỉnh Long Môn! Đợi đêm xuống
            val dayTime = level.dayTime % 24000
            if (dayTime in 13000..23000) {
                // Đêm rồi! Gọi vòi rồng và tiến hóa
                createTornado(level, target)
                
                // Tiến hóa
                com.toancao.pokemonai.evolution.EvolutionManager.forceEvolve(entity, "gyarados")
                
                // Gỡ tag để không chạy lại
                living.removeTag("dragon_gate_challenger")
                com.toancao.pokemonai.utils.DebugUtils.logEvent("Magikarp at $target has evolved via Dragon Gate!")
            } else {
                // Lảng vảng quanh đỉnh thác
                living.deltaMovement = living.deltaMovement.scale(0.5)
            }
        }
    }

    private fun createTornado(level: ServerLevel, pos: BlockPos) {
        // Tạo vòi rồng xoáy lên (particles)
        val radius = 2.0
        for (i in 0..50) {
            val angle = i * 0.5
            val yOffset = i * 0.2
            val px = pos.x + radius * Math.cos(angle)
            val pz = pos.z + radius * Math.sin(angle)
            level.sendParticles(ParticleTypes.CLOUD, px, pos.y + yOffset, pz, 2, 0.1, 0.1, 0.1, 0.05)
            level.sendParticles(ParticleTypes.SPLASH, px, pos.y + yOffset, pz, 5, 0.1, 0.1, 0.1, 0.1)
        }
    }
}
