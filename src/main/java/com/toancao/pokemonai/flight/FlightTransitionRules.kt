package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.entity.player.Player
import kotlin.random.Random

object FlightTransitionRules {

    /**
     * Đánh giá các điều kiện chuyển đổi trạng thái khi Pokémon đang ở dưới đất (GROUNDED).
     */
    fun evaluateGrounded(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile

        if (!machine.hasPlayerInRadius) return null
        
        // Không tự ý cất cánh nếu vừa bị đánh (để người chơi dễ dàng chiến đấu)
        if (mob.lastHurtByMob != null && mob.tickCount - mob.lastHurtByMobTimestamp < 300) {
            return null
        }

        // Kiểm tra tỉ lệ cất cánh/lơ lửng mỗi giây (20 ticks) thay vì mỗi tick
        if (globalTick % 20 == 0) {
            val isTouchingWater = mob.isInWater || mob.isUnderWater

            // Yêu cầu thể lực tối thiểu 10% để có thể lơ lửng trên mặt nước
            if (isTouchingWater && profile.currentStamina >= profile.config.maxFlightTicks * 0.1) {
                if (Random.nextDouble() < profile.config.waterHoverChance) {
                    return FlightState.WATER_HOVERING
                }
            }
            
            // Yêu cầu thể lực tối thiểu 80% để cất cánh bay lên trời (100% nếu đã nảy 3 lần)
            val requiredTakeoffStamina = if (profile.bounceCount >= 3) 1.0 else 0.8
            val staminaPercent = profile.currentStamina / profile.config.maxFlightTicks.toDouble()
            if (staminaPercent >= requiredTakeoffStamina) {
                // Tỉ lệ cất cánh tăng dần theo lượng stamina hiện có (tính theo công thức)
                val dynamicTakeoffChance = profile.config.baseTakeoffChance * staminaPercent
                if (Random.nextDouble() < dynamicTakeoffChance) {
                    return FlightState.TAKING_OFF
                }
            }
        }

        return null
    }

    /**
     * Đánh giá các điều kiện chuyển đổi trạng thái khi Pokémon đang bay (FLYING).
     */
    fun evaluateFlying(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile
        // Hết stamina thì chuyển trạng thái hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }
        
        // Rời khỏi bán kính người chơi thì hạ cánh
        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            // Tỉ lệ mỏi cánh ngẫu nhiên
            if (Random.nextDouble() < profile.config.baseLandingChance) {
                return FlightState.LANDING
            }

            // Tỉ lệ chuyển sang lơ lửng trên mặt đất (giảm từ 85% xuống 10% để bớt giật cục)
            if (profile.config.canGroundHover && Random.nextDouble() < 0.10) {
                return FlightState.GROUND_HOVERING
            }

            // Tỉ lệ chuyển sang lượn vòng
            val currentHeight = mob.y - FlightHelpers.estimateGroundY(mob)
            if (currentHeight >= profile.currentPreferredHeight * 0.8) {
                if (profile.config.circularFlightChance > 0 && Random.nextDouble() < profile.config.circularFlightChance) {
                    return FlightState.CIRCULAR_FLYING
                }
            }
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang bay lượn vòng.
     */
    fun evaluateCircularFlying(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile
        // Hết stamina thì hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        // Rời khỏi bán kính người chơi thì hạ cánh
        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        // Tính toán thời gian để hoàn thành ít nhất 1 vòng tròn
        val radius = profile.config.circularFlightRadius
        val speed = profile.config.flightSpeed
        val circumference = 2 * Math.PI * radius
        // Giả sử speed = số block/tick (mặc định speed là 0.5 ~ 0.5 block/tick)
        val timeForOneCircle = (circumference / speed).toInt()
        
        // Đảm bảo bay ít nhất 1 vòng (hoặc theo cấu hình nếu cấu hình lớn hơn)
        val duration = kotlin.math.max(timeForOneCircle, profile.config.circularFlightDuration)
        
        // Hết thời gian bay lượn vòng thì quay về bay thẳng
        if (profile.ticksInCurrentState >= duration) {
            return FlightState.FLYING
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang bay lơ lửng trên mặt đất.
     */
    fun evaluateGroundHovering(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile
        // Hết stamina thì hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        // Rời khỏi bán kính người chơi thì hạ cánh
        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            // Tỉ lệ kết thúc lơ lửng và bay thẳng tiếp
            if (!profile.config.hoverOnly && Random.nextDouble() < 0.15) { // Chỉ 15% cơ hội thoát mỗi 5s -> lơ lửng rất lâu
                return FlightState.FLYING
            }
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang lơ lửng trên mặt nước.
     */
    fun evaluateWaterHovering(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val profile = machine.profile
        // Hết stamina thì hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            // Có tỉ lệ nhỏ quay lại bay thẳng nếu không phải loài chỉ lơ lửng
            if (!profile.config.hoverOnly && Random.nextDouble() < 0.15) {
                return FlightState.FLYING
            }
        }

        return null
    }
}