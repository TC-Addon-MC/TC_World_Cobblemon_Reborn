package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.entity.player.Player
import kotlin.random.Random

object FlightTransitionRules {

    /**
     * Đánh giá các điều kiện chuyển đổi trạng thái khi Pokémon đang ở dưới đất (GROUNDED).
     */
    fun evaluateGrounded(pokemon: PokemonEntity, profile: CustomFlightProfile, globalTick: Int): FlightState? {
        val mob = pokemon as net.minecraft.world.entity.Mob

        if (!isPlayerWithinFlyRadius(mob, profile)) return null

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
    fun evaluateFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, globalTick: Int): FlightState? {
        val mob = pokemon as net.minecraft.world.entity.Mob
        // Hết stamina thì chuyển trạng thái hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }
        
        // Rời khỏi bán kính người chơi thì hạ cánh
        if (!isPlayerWithinFlyRadius(mob, profile)) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            // Tỉ lệ mỏi cánh ngẫu nhiên
            if (Random.nextDouble() < profile.config.baseLandingChance) {
                return FlightState.LANDING
            }

            // Ưu tiên: Tỉ lệ chuyển sang lơ lửng trên mặt đất đối với các pokemon được phép
            if (profile.config.canGroundHover && Random.nextDouble() < 0.85) { // 85% xác suất mỗi 5s
                return FlightState.GROUND_HOVERING
            }

            // Tỉ lệ chuyển sang lượn vòng (chỉ khi đã đạt đủ độ cao để tránh giật lag)
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
    fun evaluateCircularFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, globalTick: Int): FlightState? {
        val mob = pokemon as net.minecraft.world.entity.Mob
        // Hết stamina thì hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        // Rời khỏi bán kính người chơi thì hạ cánh
        if (!isPlayerWithinFlyRadius(mob, profile)) {
            return FlightState.LANDING
        }

        // Hết thời gian bay lượn vòng thì quay về bay thẳng
        val duration = kotlin.math.max(100, profile.config.circularFlightDuration)
        if (profile.ticksInCurrentState >= duration) {
            return FlightState.FLYING
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang bay lơ lửng trên mặt đất.
     */
    fun evaluateGroundHovering(pokemon: PokemonEntity, profile: CustomFlightProfile, globalTick: Int): FlightState? {
        val mob = pokemon as net.minecraft.world.entity.Mob
        // Hết stamina thì hạ cánh
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        // Rời khỏi bán kính người chơi thì hạ cánh
        if (!isPlayerWithinFlyRadius(mob, profile)) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            // Tỉ lệ kết thúc lơ lửng và bay thẳng tiếp
            if (Random.nextDouble() < 0.15) { // Chỉ 15% cơ hội thoát mỗi 5s -> lơ lửng rất lâu
                return FlightState.FLYING
            }
        }

        return null
    }

    /**
     * Hàm phụ trợ kiểm tra xem người chơi có trong bán kính cho phép cất cánh không.
     */
    private fun isPlayerWithinFlyRadius(mob: net.minecraft.world.entity.Mob, profile: CustomFlightProfile): Boolean {
        val level = mob.level() ?: return false
        val r = profile.config.activationRadius
        val ySearch = kotlin.math.max(r, profile.currentPreferredHeight + 10.0)

        val players = level.getEntitiesOfClass(
            Player::class.java,
            AABB(
                mob.x - r, mob.y - ySearch, mob.z - r,
                mob.x + r, mob.y + ySearch, mob.z + r
            )
        )
        return players.isNotEmpty()
    }
}
