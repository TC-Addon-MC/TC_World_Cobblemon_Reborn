package com.toancao.pokemonai.utils

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos

object MovementUtils {
    fun applyVerticalVelocity(entity: Entity, height: Double) {
        val current = entity.deltaMovement
        entity.deltaMovement = Vec3(current.x, kotlin.math.sqrt(height * 0.1), current.z)
        entity.hasImpulse = true
    }

    fun applySwimAgainstCurrentVelocity(entity: Entity, direction: Vec3) {
        entity.deltaMovement = entity.deltaMovement.add(direction.scale(0.2))
        entity.hasImpulse = true
    }

    fun getVectorTo(posA: BlockPos, posB: BlockPos): Vec3 {
        return Vec3(
            posB.x.toDouble() - posA.x.toDouble(),
            posB.y.toDouble() - posA.y.toDouble(),
            posB.z.toDouble() - posA.z.toDouble()
        ).normalize()
    }

    fun isPushedBack(entity: Entity, lastPos: Vec3): Boolean {
        return entity.distanceToSqr(lastPos) < 0.01
    }
}
