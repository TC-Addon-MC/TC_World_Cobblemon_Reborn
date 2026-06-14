@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.utils.MovementUtils
import com.toancao.pokemonai.utils.RandomUtils
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity

class JumpOutOfWaterGoal(
    private val entity: PokemonEntity,
    private val jumpChance: Float = RandomUtils.Probability.LOW.chance,
    private val cooldownTicks: Int = 600
) : Goal() {
    private var cooldown = 0
    private var state = 0 // 0: idle, 1: swimming to surface
    private var timeoutTicks = 0

    override fun canUse(): Boolean {
        if (cooldown > 0) {
            cooldown--
            return false
        }
        val isInWater = (entity as LivingEntity).isInWater
        return isInWater && com.toancao.pokemonai.utils.AIFilter.isEligible(entity) && RandomUtils.chance(jumpChance)
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
        
        val isAtSurface = !level.getFluidState(pos.above()).`is`(net.minecraft.tags.FluidTags.WATER)
        
        if (isAtSurface) {
            val attack = le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            val sizeWeight = le.bbWidth * le.bbHeight

            val statFactor = (attack / Math.max(sizeWeight.toDouble(), 0.1)) * 0.5
            val randomFactor = Math.random() * 2.0 
            
            val height = Math.min(3.2, Math.max(2.0, 2.0 + statFactor + randomFactor))

            MovementUtils.applyVerticalVelocity(entity as net.minecraft.world.entity.Entity, height)
            
            state = 2 // Finished jumping
            cooldown = cooldownTicks
        } else {
            // Bơi dần lên mặt nước một cách êm ái thay vì giật cục
            le.deltaMovement = net.minecraft.world.phys.Vec3(le.deltaMovement.x * 0.9, 0.15, le.deltaMovement.z * 0.9)
            timeoutTicks++
            if (timeoutTicks > 200) { // Hết thời gian bơi lên mà chưa tới mặt nước (kẹt)
                state = 2
                cooldown = cooldownTicks
            }
        }
    }

    override fun canContinueToUse(): Boolean {
        return state == 1 && (entity as LivingEntity).isInWater
    }
}

