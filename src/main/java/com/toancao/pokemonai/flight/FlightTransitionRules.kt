package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.entity.player.Player
import kotlin.random.Random

object FlightTransitionRules {

    /**
     * Đánh giá các điều kiện chuyển đổi trạng thái khi Pokémon đang ở dưới đất (GROUNDED).
     */
    fun evaluateGrounded(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile

        if (!machine.hasPlayerInRadius) return null
        
        if (mob.lastHurtByMob != null && mob.tickCount - mob.lastHurtByMobTimestamp < 300) {
            return null
        }

        if (globalTick % 20 == 0) {
            val isTouchingWater = mob.isInWater || mob.isUnderWater

            if (isTouchingWater && profile.currentStamina >= profile.config.maxFlightTicks * 0.1) {
                if (Random.nextDouble() < profile.config.waterHoverChance) {
                    return FlightState.WATER_HOVERING
                }
            }
            
            val requiredTakeoffStamina = if (profile.bounceCount >= 3) 1.0 else 0.8
            val staminaPercent = profile.currentStamina / profile.config.maxFlightTicks.toDouble()
            if (staminaPercent >= requiredTakeoffStamina) {
                val dynamicTakeoffChance = profile.config.baseTakeoffChance * staminaPercent
                if (Random.nextDouble() < dynamicTakeoffChance) {
                    return FlightState.TAKING_OFF
                }
            }
        }

        return null
    }

    /**
     * Đánh giá các điều kiện chuyển đổi trạng thái khi Pokémon đang bay (FLYING).
     */
    fun evaluateFlying(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }
        
        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            if (Random.nextDouble() < profile.config.baseLandingChance) {
                return FlightState.LANDING
            }

            if (profile.config.canGroundHover && Random.nextDouble() < 0.10) {
                return FlightState.GROUND_HOVERING
            }

            val currentHeight = mob.y - FlightHelpers.estimateGroundY(mob)
            if (currentHeight >= profile.currentPreferredHeight * 0.8) {
                if (profile.config.circularFlightChance > 0 && Random.nextDouble() < profile.config.circularFlightChance) {
                    return FlightState.CIRCULAR_FLYING
                }
            }
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang bay lượn vòng.
     */
    fun evaluateCircularFlying(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        val radius = profile.config.circularFlightRadius
        val speed = profile.config.flightSpeed
        val circumference = 2 * Math.PI * radius
        val timeForOneCircle = (circumference / speed).toInt()
        
        val duration = kotlin.math.max(timeForOneCircle, profile.config.circularFlightDuration)
        
        if (profile.ticksInCurrentState >= duration) {
            return FlightState.FLYING
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang bay lơ lửng trên mặt đất.
     */
    fun evaluateGroundHovering(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val mob = machine.pokemon as net.minecraft.world.entity.Mob
        val profile = machine.profile
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            if (!profile.config.hoverOnly && Random.nextDouble() < 0.15) {
                return FlightState.FLYING
            }
        }

        return null
    }

    /**
     * Đánh giá chuyển đổi trạng thái khi đang lơ lửng trên mặt nước.
     */
    fun evaluateWaterHovering(machine: NormalFlightStateMachine, globalTick: Int): FlightState? {
        val profile = machine.profile
        if (profile.currentStamina <= 0) {
            return FlightState.LANDING
        }

        if (!machine.hasPlayerInRadius) {
            return FlightState.LANDING
        }

        if (globalTick % 100 == 0) {
            if (!profile.config.hoverOnly && Random.nextDouble() < 0.15) {
                return FlightState.FLYING
            }
        }

        return null
    }
}