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
    private var cooldown = 600 + (entity as LivingEntity).level().random.nextInt(200) // 30-40 seconds initial
    private var state = 0 // 0: idle, 1: swimming to surface
    private var timeoutTicks = 0

    override fun canUse(): Boolean {
        if (cooldown > 0) {
            cooldown--
            return false
        }
        
        val le = entity as LivingEntity
        val isInWater = le.isInWater
        
        // Reset cooldown ngay lập tức để mỗi 30-40s chỉ có 1 cơ hội "đổ xúc xắc" nhảy.
        // Điều này giúp trong một hồ có nhiều cá, mỗi chu kỳ 30-40s chỉ có "vài con" ngẫu nhiên trúng tỷ lệ (jumpChance) và bật lên.
        cooldown = 600 + le.level().random.nextInt(200)

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
        return com.toancao.pokemonai.utils.AIFilter.isEligible(entity)
    }

    override fun start() {
        state = 1
        timeoutTicks = 0
    }

    override fun tick() {
        if (state != 1) return
        val le = entity as LivingEntity
        val level = le.level()
        val pos = le.blockPosition()
        
        // Tìm block mặt nước thực tế
        var surfaceBlockPos = pos
        var current = pos
        while (level.getFluidState(current).`is`(net.minecraft.tags.FluidTags.WATER) && current.y < level.maxBuildHeight) {
            surfaceBlockPos = current
            current = current.above()
        }
        
        val headY = le.y + le.eyeHeight
        
        // Nếu đầu cá đã chạm hoặc vượt qua block nước trên cùng
        if (headY >= surfaceBlockPos.y) {
            // Tính toán vận tốc nhảy
            val attack = le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            val sizeWeight = le.bbWidth * le.bbHeight
            val statFactor = attack / Math.max(sizeWeight.toDouble(), 0.1)
            
            // Chuyển đổi statFactor thành lực nhảy cộng thêm (giới hạn tối đa 0.3 cho cá có attack rất khủng)
            val statBonus = Math.min(0.3, statFactor * 0.005)
            // Hệ số ngẫu nhiên từ 0.0 đến 0.2
            val randomBonus = Math.random() * 0.2 
            
            // Trong Minecraft, deltaMovement.y = 1.0 sẽ tạo ra cú nhảy cao ~6 block (do gia tốc trọng trường)
            // Lực cơ bản = 0.7 (~3.5 block)
            // Max bình thường = 0.7 + 0.1 (stat tb) + 0.2 (random) = 1.0 (~6 block)
            // Max đột biến (attack siêu cao) = 0.7 + 0.3 + 0.2 = 1.2 (~8.5 block)
            val jumpPower = 0.7 + statBonus + randomBonus
            
            // Chỉ nhảy vọt thẳng đứng lên (Splash), vì Magikarp bơi rất yếu nên không nhảy về phía trước
            le.deltaMovement = net.minecraft.world.phys.Vec3(le.deltaMovement.x * 0.1, jumpPower, le.deltaMovement.z * 0.1)
            le.hasImpulse = true
            
            // Phát particles tại vị trí hiện tại
            if (level is net.minecraft.server.level.ServerLevel) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, le.x, le.y, le.z, 30, 0.5, 0.5, 0.5, 0.2)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, le.x, le.y, le.z, 15, 0.5, 0.5, 0.5, 0.1)
            }
            
            state = 2
            cooldown = 600 + level.random.nextInt(200) // Reset cooldown 30-40 seconds
        } else {
            // Bơi thẳng đứng lên
            le.deltaMovement = net.minecraft.world.phys.Vec3(le.deltaMovement.x * 0.5, 0.25, le.deltaMovement.z * 0.5)
            timeoutTicks++
            // Thời gian chờ dự phòng (failsafe) 30 giây phòng trường hợp bị kẹt bởi entity khác hoặc dòng chảy phức tạp
            if (timeoutTicks > 600) { 
                state = 2
                cooldown = 600 + level.random.nextInt(200)
            }
        }
    }

    override fun canContinueToUse(): Boolean {
        return state == 1 && (entity as LivingEntity).isInWater
    }
}

