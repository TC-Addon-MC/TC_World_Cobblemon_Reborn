package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import com.toancao.pokemonai.events.DragonGateEvent

class DragonGateFreeSwimGoal(private val pokemonEntity: PokemonEntity) : Goal() {
    private val entity: LivingEntity = pokemonEntity as LivingEntity
    private var wanderTarget: Vec3? = null
    private var wanderTicks = 0

    override fun canUse(): Boolean {
        return entity.tags.contains("dragon_gate_free_swim")
    }

    override fun canContinueToUse() = canUse()

    override fun start() {
        wanderTicks = 0
        pickNewWanderTarget()
    }

    override fun tick() {
        wanderTicks++
        if (wanderTicks > 200 || wanderTarget == null || entity.distanceToSqr(wanderTarget!!) < 9.0) {
            pickNewWanderTarget()
            wanderTicks = 0
        }

        val target = wanderTarget ?: return
        
        val mob = entity as? net.minecraft.world.entity.Mob
        if (mob != null) {
            mob.moveControl.setWantedPosition(target.x, target.y, target.z, 1.2)
        }
    }

    private fun pickNewWanderTarget() {
        var nearestBottom: BlockPos? = null
        var minDist = Double.MAX_VALUE
        for (b in DragonGateEvent.bottomBlocks) {
            val d = b.distSqr(entity.blockPosition())
            if (d < minDist) {
                minDist = d
                nearestBottom = b
            }
        }

        if (nearestBottom != null) {
            // Hướng về bottom và đi lố qua (overshoot) để bơi dạt đi xa
            val dx = nearestBottom.x - entity.x
            val dz = nearestBottom.z - entity.z
            val len = Math.sqrt(dx * dx + dz * dz)
            if (len > 0.1) {
                val nx = dx / len
                val nz = dz / len
                wanderTarget = Vec3(nearestBottom.x + nx * 40.0, nearestBottom.y.toDouble(), nearestBottom.z + nz * 40.0)
            } else {
                wanderTarget = Vec3(nearestBottom.x.toDouble(), nearestBottom.y.toDouble(), nearestBottom.z.toDouble())
            }
        } else {
            // Fallback nếu không có bottom
            val radius = 30.0
            val ox = (entity.level().random.nextDouble() - 0.5) * 2 * radius
            val oz = (entity.level().random.nextDouble() - 0.5) * 2 * radius
            wanderTarget = Vec3(entity.x + ox, 62.0, entity.z + oz)
        }
    }
}
