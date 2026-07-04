package com.toancao.pokemonai.utils

import com.toancao.pokemonai.flight.CustomFlightProfile
import kotlin.random.Random

object StaminaManager {

    /**
     * Hồi phục thể lực mỗi giây khi Pokémon ở trên mặt đất.
     */
    fun recoverStamina(profile: CustomFlightProfile, globalTick: Int) {
        // Hồi phục 1 lần mỗi giây (20 ticks)
        if (globalTick % 20 == 0) {
            val maxStamina = profile.config.maxFlightTicks.toDouble()
            if (profile.currentStamina < maxStamina) {
                // Tốc độ hồi phục từ 1% đến staminaRecoveryMaxRate (mặc định 10%, sau khi nảy còn 5%)
                val restorePercent = Random.nextDouble(0.01, profile.staminaRecoveryMaxRate)
                profile.currentStamina = kotlin.math.min(maxStamina, profile.currentStamina + maxStamina * restorePercent)
            }
        }
    }

    private fun getConsumptionMultiplier(pokemonEntity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity): Double {
        val pokemon = pokemonEntity.pokemon
        val weightKg = pokemon.form.weight / 10.0
        val strength = pokemon.attack.toDouble()
        
        // Phối hợp cách tính cũ (hệ số gốc là 1.0) với Cân nặng (phạt) và Sức mạnh (thưởng)
        val weightPenalty = weightKg / 50.0 // Cứ 50kg thì cộng thêm 1 hệ số phạt
        val strengthBonus = strength / 100.0 // Cứ 100 Attack thì được 1 hệ số thưởng
        
        // Hệ số cuối cùng, giới hạn tối thiểu 0.2 để không bay vĩnh viễn, tối đa 5.0 để không rớt ngay
        return kotlin.math.max(0.2, kotlin.math.min(5.0, (1.0 + weightPenalty) / (1.0 + strengthBonus)))
    }

    /**
     * Trừ thể lực khi Pokémon đang bay.
     */
    fun consumeStamina(profile: CustomFlightProfile) {
        val multiplier = getConsumptionMultiplier(profile.pokemon)
        profile.currentStamina = kotlin.math.max(0.0, profile.currentStamina - (1.0 * multiplier))
    }

    /**
     * Trừ thể lực khi Pokémon đang lơ lửng trên mặt nước.
     * Tiêu hao ít hơn so với bay trên trời. Có tỉ lệ nhỏ hồi phục thể lực nếu đang bay trên nước.
     */
    fun consumeStaminaHover(profile: CustomFlightProfile) {
        // Không tốn stamina khi hovering trên mặt nước theo yêu cầu
        // val multiplier = getConsumptionMultiplier(profile.pokemon)
        // profile.currentStamina = kotlin.math.max(0.0, profile.currentStamina - (0.2 * multiplier))

        // Hồi phục 1 lượng rất nhỏ mỗi giây (20 ticks) khi lơ lửng trên nước
        if (profile.ticksInCurrentState > 0 && profile.ticksInCurrentState % 20 == 0) {
            val maxStamina = profile.config.maxFlightTicks.toDouble()
            if (profile.currentStamina < maxStamina) {
                // Hồi phục 0.5% - 1% lượng stamina tối đa mỗi giây (rất ít)
                val restorePercent = Random.nextDouble(0.005, 0.01)
                profile.currentStamina = kotlin.math.min(maxStamina, profile.currentStamina + maxStamina * restorePercent)
            }
        }
    }

    /**
     * Bơm thể lực khi Pokémon nảy lên từ mặt nước.
     * Cắt giảm tốc độ hồi phục sau khi đã được bơm.
     */
    fun pumpStaminaOnBounce(profile: CustomFlightProfile) {
        val restored = if (profile.config.maxFlightTicks > 0) profile.config.maxFlightTicks / 8.0 else 100.0
        val maxStamina = profile.config.maxFlightTicks.toDouble()
        profile.currentStamina = kotlin.math.min(maxStamina, profile.currentStamina + restored)

        // Giảm tốc độ hồi phục tối đa xuống còn 5%
        profile.staminaRecoveryMaxRate = 0.05
    }

    /**
     * Phục hồi tốc độ hồi phục thể lực về mặc định khi đáp đất an toàn.
     */
    fun resetRecoveryRate(profile: CustomFlightProfile) {
        profile.staminaRecoveryMaxRate = 0.10
    }
}
