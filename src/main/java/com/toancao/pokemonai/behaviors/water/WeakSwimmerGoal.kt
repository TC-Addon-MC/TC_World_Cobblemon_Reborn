package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.tags.FluidTags
import net.minecraft.world.phys.Vec3

class WeakSwimmerGoal(private val entity: PokemonEntity) : Goal() {
    override fun canUse(): Boolean {
        val livingEntity = entity as LivingEntity
        return livingEntity.isInWater
    }

    override fun canContinueToUse(): Boolean {
        val livingEntity = entity as LivingEntity
        return livingEntity.isInWater
    }

    override fun tick() {
        val livingEntity = entity as LivingEntity
        val level = livingEntity.level()
        val pos = livingEntity.blockPosition()
        val fluidState = level.getFluidState(pos)

        if (fluidState.`is`(FluidTags.WATER) && !fluidState.isSource) {
            // Lấy hướng dòng chảy
            val flow = fluidState.getFlow(level, pos)
            if (flow.lengthSqr() > 0) {
                // Áp dụng thêm lực đẩy xuôi theo dòng chảy vì Magikarp bơi yếu
                val currentMovement = livingEntity.deltaMovement
                livingEntity.deltaMovement = currentMovement.add(flow.x * 0.05, flow.y * 0.05, flow.z * 0.05)
            }
        }
    }
}
