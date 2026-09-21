package com.toancao.pokemonai.behaviors.combat

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

class BullRushChargeGoal(private val entity: PokemonEntity) : Goal() {

    enum class ChargeState { WINDUP, CHARGING, COOLDOWN }

    private var target: LivingEntity? = null
    private var state = ChargeState.WINDUP
    private var ticks = 0
    private var chargeVec: Vec3 = Vec3.ZERO

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        if (entity.isBattling) return false
        if (entity.getHerdData().isStampeding) return false
        val currentTarget = entity.target ?: return false
        if (!currentTarget.isAlive) return false

        val distSq = entity.distanceToSqr(currentTarget)
        return distSq in 16.0..256.0
    }

    override fun start() {
        target = entity.target
        state = ChargeState.WINDUP
        ticks = 0
        entity.navigation.stop()
    }

    override fun tick() {
        val curTarget = target ?: return
        val level = entity.level() as? ServerLevel ?: return
        ticks++

        when (state) {
            ChargeState.WINDUP -> {
                entity.lookControl.setLookAt(curTarget, 60.0f, 60.0f)
                if (ticks % 5 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, entity.x, entity.y, entity.z, 5, 0.3, 0.1, 0.3, 0.05)
                }
                if (ticks >= 25) {
                    state = ChargeState.CHARGING
                    ticks = 0
                    entity.isSprinting = true
                    val look = curTarget.position().subtract(entity.position()).normalize()
                    chargeVec = Vec3(look.x * 0.85, 0.0, look.z * 0.85)
                    level.playSound(null, entity.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.NEUTRAL, 1.2f, 0.8f)
                }
            }
            ChargeState.CHARGING -> {
                entity.isSprinting = true
                entity.deltaMovement = chargeVec

                val frontPos = BlockPos.containing(entity.x + chargeVec.x * 1.5, entity.y + 0.5, entity.z + chargeVec.z * 1.5)
                BlockBreakHandler.tryBreakBlocksInBox(level, frontPos.offset(-1, 0, -1), frontPos.offset(1, 1, 1))

                if (entity.distanceToSqr(curTarget) < 4.0) {
                    val attackPower = (entity.pokemon.attack / 5.0f).coerceAtLeast(6.0f)
                    curTarget.hurt(entity.damageSources().mobAttack(entity), attackPower)
                    curTarget.knockback(1.5, -chargeVec.x, -chargeVec.z)
                    level.playSound(null, entity.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.0f, 1.2f)

                    entity.isSprinting = false
                    state = ChargeState.COOLDOWN
                    ticks = 0
                }

                if (ticks >= 35) {
                    entity.isSprinting = false
                    state = ChargeState.COOLDOWN
                    ticks = 0
                }
            }
            ChargeState.COOLDOWN -> {
                entity.isSprinting = false
                if (ticks >= 40) {
                    stop()
                }
            }
        }
    }

    override fun stop() {
        entity.isSprinting = false
        target = null
        state = ChargeState.WINDUP
        ticks = 0
    }
}
