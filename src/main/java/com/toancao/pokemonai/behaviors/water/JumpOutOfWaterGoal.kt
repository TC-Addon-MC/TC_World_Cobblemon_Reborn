@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.utils.MovementUtils
import com.toancao.pokemonai.utils.RandomUtils
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity

class JumpOutOfWaterGoal(
    private val entity: PokemonEntity,
    private val jumpChance: Float = RandomUtils.Probability.MEDIUM.chance,
    private val cooldownTicks: Int = 200
) : Goal() {
    private var cooldown = 0

    override fun canUse(): Boolean {
        if (cooldown > 0) {
            cooldown--
            return false
        }
        val isInWater = (entity as LivingEntity).isInWater
        return isInWater && com.toancao.pokemonai.utils.AIFilter.isEligible(entity) && RandomUtils.chance(jumpChance)
    }

    override fun start() {
        val attack = (entity as LivingEntity).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
        val sizeWeight = (entity as LivingEntity).bbWidth * (entity as LivingEntity).bbHeight

        // Base jump height is 4.0. Attack increases height, size (weight) decreases it.
        val statFactor = (attack / Math.max(sizeWeight.toDouble(), 0.1)) * 0.5
        val randomFactor = Math.random() * 2.0 // Random bonus 0.0 to 2.0
        
        // Cap height to 2 meters (7 feet). In MovementUtils, a height of 3.2 results in 2 blocks of physical jump height.
        val height = Math.min(3.2, Math.max(2.0, 2.0 + statFactor + randomFactor))

        MovementUtils.applyVerticalVelocity(entity as net.minecraft.world.entity.Entity, height)
        cooldown = cooldownTicks
        
        com.toancao.pokemonai.utils.DebugUtils.logAction(entity, "JumpOutOfWater", "Jumped with height " + String.format("%.2f", height) + " (Attack: " + String.format("%.1f", attack) + ", SizeWeight: " + String.format("%.2f", sizeWeight) + ")")
    }

    override fun canContinueToUse(): Boolean {
        return false // One-shot action
    }
}

