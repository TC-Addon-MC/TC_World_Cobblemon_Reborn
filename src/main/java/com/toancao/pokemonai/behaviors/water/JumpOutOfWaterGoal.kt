@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.utils.MovementUtils
import com.toancao.pokemonai.utils.RandomUtils
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity

class JumpOutOfWaterGoal(
    private val entity: PokemonEntity,
    private val jumpChance: Float = RandomUtils.Probability.LOW.chance
) : Goal() {
    private var cooldown = 100 + (entity as LivingEntity).level().random.nextInt(20) // ~5 seconds initial
    private var state = 0 // 0: idle, 1: swimming to surface
    private var timeoutTicks = 0

    init {
        this.flags = java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK, net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP)
    }

    override fun canUse(): Boolean {
        if (cooldown > 0) {
            cooldown--
            return false
        }
        
        val le = entity as LivingEntity
        val isInWater = le.isInWater
        
        // Check mỗi 5s
        cooldown = 100 + le.level().random.nextInt(20)

        if (!isInWater) return false
        
        // Tỉ lệ 20% nhẩy cho bất kì con cá nào
        if (le.level().random.nextFloat() >= 0.20f) return false

        if (!isInWater) return false
        
        // Kiểm tra xem phía trên mặt nước có bị chặn (như băng, trần hang) không
        val level = le.level()
        var currentPos = le.blockPosition()
        var isBlocked = false
        while (currentPos.y < level.maxBuildHeight) {
            val state = level.getBlockState(currentPos)
            if (!state.fluidState.`is`(net.minecraft.tags.FluidTags.WATER)) {
                // Thoát khỏi mặt nước, kiểm tra xem có phải không khí không (cần không gian để nhảy)
                if (!state.isAir && !level.getBlockState(currentPos.above()).isAir) {
                    isBlocked = true
                }
                break
            }
            currentPos = currentPos.above()
        }
        
        if (isBlocked) return false // Nếu bị cản thì không thèm bơi lên làm gì

        // Bỏ qua RandomUtils.chance vì cooldown (30-40s) đã đóng vai trò làm giãn cách thời gian nhảy rồi.
        // Khi hết cooldown, cá chắc chắn sẽ nhảy (nếu không bị cản).
        if (!com.toancao.pokemonai.utils.AIFilter.isEligible(entity)) return false
        val species = com.toancao.pokemonai.compat.CobblemonBridge.getSpeciesName(entity)
        return species == "magikarp"
    }

    override fun start() {
        state = 1
        timeoutTicks = 0
    }

    override fun tick() {
        if (state != 1) return
        val le = entity as LivingEntity
        val level = le.level()
        
        // Nếu cá đã vọt hẳn ra khỏi nước (không còn bị lực cản của nước)
        if (!le.isInWater) {
            // Tính toán vận tốc nhảy
            val attack = le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            val sizeWeight = le.bbWidth * le.bbHeight
            val statFactor = attack / Math.max(sizeWeight.toDouble(), 0.1)
            
            val statBonus = Math.min(0.3, statFactor * 0.005)
            val randomBonus = Math.random() * 0.2 
            
            val jumpPower = 0.7 + statBonus + randomBonus
            
            // Lực búng lúc này hoàn toàn không bị nước cản lại!
            le.deltaMovement = net.minecraft.world.phys.Vec3(le.deltaMovement.x * 0.1, jumpPower, le.deltaMovement.z * 0.1)
            le.hasImpulse = true
            
            if (level is net.minecraft.server.level.ServerLevel) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, le.x, le.y, le.z, 30, 0.5, 0.5, 0.5, 0.2)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, le.x, le.y, le.z, 15, 0.5, 0.5, 0.5, 0.1)
            }
            
            state = 2
            timeoutTicks = 0
            cooldown = 100 + level.random.nextInt(20)
        } else {
            // Vẫn đang trong nước, tìm mặt nước để bơi lên
            var current = le.blockPosition()
            var surfaceBlockPos = current
            while (level.getFluidState(current).`is`(net.minecraft.tags.FluidTags.WATER) && current.y < level.maxBuildHeight) {
                surfaceBlockPos = current
                current = current.above()
            }

            // Ép bơi lố qua mặt nước 1.5 block để chắc chắn toàn bộ thân cá thoát khỏi nước
            val mob = le as net.minecraft.world.entity.Mob
            mob.moveControl.setWantedPosition(
                le.x,
                surfaceBlockPos.y.toDouble() + 1.5,
                le.z,
                1.0
            )
            
            timeoutTicks++
            // Thời gian chờ dự phòng (failsafe)
            if (timeoutTicks > 600) { 
                state = 2
                timeoutTicks = 0
                cooldown = 100 + level.random.nextInt(20)
            }
        }
    }

    override fun canContinueToUse(): Boolean {
        val le = entity as LivingEntity
        if (state == 1) return true // Tiếp tục duy trì để tick() xử lý thời khắc thoát khỏi nước

        if (state == 2) {
            timeoutTicks++
            // Đợi ít nhất 10 tick cho cá văng lên không trung. Sau đó nếu chạm nước hoặc chạm đất thì ngưng.
            if (timeoutTicks > 10 && le.isInWater) return false
            if (timeoutTicks > 10 && le.onGround()) return false
            // Duy trì trạng thái bay lượn cho đến khi chạm nước hoặc đất
            return true
        }
        return false
    }
}

