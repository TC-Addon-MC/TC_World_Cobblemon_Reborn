package com.toancao.pokemonai.utils

import net.minecraft.world.entity.Entity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.FlowingFluid

object WaterUtils {
    fun isNearStrongCurrent(entity: Entity): Boolean {
        return detectWaterfall(entity, 3)
    }

    fun detectWaterfall(entity: Entity, radius: Int = 3): Boolean {
        val world = entity.level()
        val pos = entity.blockPosition()
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                for (y in -radius..radius) {
                    val checkPos = pos.offset(x, y, z)
                    val fluid = world.getFluidState(checkPos)
                    if (fluid.`is`(Fluids.WATER) && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) {
                        val aboveFluid = world.getFluidState(checkPos.above())
                        if (aboveFluid.`is`(Fluids.WATER) && aboveFluid.hasProperty(FlowingFluid.FALLING) && aboveFluid.getValue(FlowingFluid.FALLING)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun findUpperWaterTarget(entity: Entity): BlockPos? {
        val upPos = entity.blockPosition().above()
        val world = entity.level()
        if (world.getFluidState(upPos).`is`(Fluids.WATER)) {
            return upPos
        }
        return null
    }

    fun getWaterDepth(entity: Entity): Int {
        var depth = 0
        var pos = entity.blockPosition()
        val world = entity.level()
        while (world.getFluidState(pos).`is`(Fluids.WATER)) {
            depth++
            pos = pos.below()
        }
        return depth
    }

    fun getNearestWaterSurface(entity: Entity, radius: Int): BlockPos? {
        val world = entity.level()
        val pos = entity.blockPosition()
        for (y in radius downTo -radius) {
            val checkPos = pos.above(y)
            val isWater = world.getFluidState(checkPos).`is`(Fluids.WATER)
            val aboveNotWater = !world.getFluidState(checkPos.above()).`is`(Fluids.WATER)
            if (isWater && aboveNotWater) {
                return checkPos
            }
        }
        return null
    }
}
