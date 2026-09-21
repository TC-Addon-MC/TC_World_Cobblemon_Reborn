package com.toancao.pokemonai.utils

import com.toancao.pokemonai.flight.CustomFlightProfile
import kotlin.random.Random

object StaminaManager {

    /**
     * Hồi phục thể lực mỗi giây khi Pokémon ở trên mặt đất.
     */
    fun recoverStamina(profile: CustomFlightProfile, globalTick: Int) {
        if (globalTick % 20 == 0) {
            val maxStamina = profile.config.maxFlightTicks.toDouble()
            if (profile.currentStamina < maxStamina) {
                val restorePercent = Random.nextDouble(0.01, profile.staminaRecoveryMaxRate)
                profile.currentStamina = kotlin.math.min(maxStamina, profile.currentStamina + maxStamina * restorePercent)
            }
        }
    }

    private fun getConsumptionMultiplier(pokemonEntity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity): Double {
        val pokemon = pokemonEntity.pokemon
        val weightKg = pokemon.form.weight / 10.0
        val strength = pokemon.attack.toDouble()
        
        val weightPenalty = weightKg / 50.0
        val strengthBonus = strength / 100.0
        
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

        if (profile.ticksInCurrentState > 0 && profile.ticksInCurrentState % 20 == 0) {
            val maxStamina = profile.config.maxFlightTicks.toDouble()
            if (profile.currentStamina < maxStamina) {
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

        profile.staminaRecoveryMaxRate = 0.05
    }

    /**
     * Phục hồi tốc độ hồi phục thể lực về mặc định khi đáp đất an toàn.
     */
    fun resetRecoveryRate(profile: CustomFlightProfile) {
        profile.staminaRecoveryMaxRate = 0.10
    }
}
