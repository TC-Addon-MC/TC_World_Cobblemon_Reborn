@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.utils.WaterUtils
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.core.BlockPos

class SwimUpwardGoal(
    private val entity: PokemonEntity
) : Goal() {

    private var targetPos: BlockPos? = null

    override fun canUse(): Boolean {
        if (!(entity as LivingEntity).isInWater) return false
        val pos = WaterUtils.findUpperWaterTarget(entity as Entity)
        if (pos != null) {
            targetPos = pos
            return true
        }
        return false
    }

    override fun tick() {
        val pos = targetPos ?: return
        // Access navigation via Mob cast (PathfinderMob extends Mob which has navigation)
        val mob = entity as Mob
        mob.navigation.moveTo(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), 1.0)
    }

    override fun canContinueToUse(): Boolean {
        val t = targetPos ?: return false
        val inWater = (entity as LivingEntity).isInWater
        val distSq = (entity as Entity).distanceToSqr(
            t.x.toDouble() + 0.5, t.y.toDouble() + 0.5, t.z.toDouble() + 0.5
        )
        return inWater && distSq > 1.0
    }
}

