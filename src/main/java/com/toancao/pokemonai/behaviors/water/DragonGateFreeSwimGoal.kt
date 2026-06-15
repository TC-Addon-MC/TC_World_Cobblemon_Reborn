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
        return false // Disabled to let dragons freely do what they want without being forced to move or stand still
    }

    override fun canContinueToUse() = canUse()

    override fun start() {
        wanderTicks = 0
        pickNewWanderTarget()
    }

    override fun tick() {
        wanderTicks++
        if (wanderTicks > 100 || wanderTarget == null || entity.distanceToSqr(wanderTarget!!) < 4.0) {
            pickNewWanderTarget()
            wanderTicks = 0
        }

        val target = wanderTarget ?: return
        val dx = target.x - entity.x
        val dy = target.y - entity.y
        val dz = target.z - entity.z

        val speed = entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 0.4
        val dist = Math.sqrt(dx * dx + dz * dz)
        
        if (dist > 0.5) {
            val horizontal = Vec3(dx, 0.0, dz).normalize()
            entity.deltaMovement = Vec3(
                horizontal.x * speed,
                (dy * 0.1).coerceIn(-0.1, 0.1),
                horizontal.z * speed
            )
            entity.lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                target
            )
            entity.yBodyRot = entity.yRotO
        }
    }

    private fun pickNewWanderTarget() {
        val radius = 10.0
        val ox = (entity.level().random.nextDouble() - 0.5) * 2 * radius
        val oz = (entity.level().random.nextDouble() - 0.5) * 2 * radius
        
        // Cố gắng tìm mặt nước xung quanh
        var targetY = entity.y
        val testPos = BlockPos(Math.floor(entity.x + ox).toInt(), entity.blockY, Math.floor(entity.z + oz).toInt())
        
        for (dy in 5 downTo -5) {
            val p = testPos.above(dy)
            if (entity.level().getFluidState(p).`is`(net.minecraft.tags.FluidTags.WATER) &&
                !entity.level().getFluidState(p.above()).`is`(net.minecraft.tags.FluidTags.WATER)
            ) {
                targetY = p.y.toDouble() - 0.5
                break
            }
        }

        wanderTarget = Vec3(entity.x + ox, targetY, entity.z + oz)
    }
}
