package com.toancao.pokemonai.flight

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import kotlin.random.Random
import com.toancao.pokemonai.flight.ai.NormalFlightAI
import com.toancao.pokemonai.flight.engine.FlightEngine

class NormalFlightStateMachine(
    val pokemon: PokemonEntity,
    private var state: FlightState = FlightState.PERCHING,
    selectedConfig: FlightConfig
) {
    val mob = pokemon as net.minecraft.world.entity.Mob

    val profile: CustomFlightProfile = CustomFlightProfile(pokemon, selectedConfig)

    private var globalTick = 0



    fun cleanup() {
        profile.debugTextDisplay?.discard()
        profile.debugTextDisplay = null
    }

    fun tick() {
        if (!isAlive()) {
            cleanup()
            return
        }
        globalTick++
        profile.ticksInCurrentState++

        updateDebugDisplay()

        val isEligible = com.toancao.pokemonai.utils.AIFilter.isEligible(pokemon)
        if (!isEligible) {
            if (state == FlightState.TAKING_OFF || state == FlightState.FLYING || state == FlightState.WATER_HOVERING || state == FlightState.GROUND_HOVERING) {
                transitionTo(FlightState.LANDING)
            }
            if (state == FlightState.GROUNDED || state == FlightState.PERCHING) {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
                return
            }
        }

        when (state) {
            FlightState.PERCHING -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
                tickPerching()
            }
            FlightState.GROUNDED -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
                tickGrounded()
            }
            FlightState.TAKING_OFF -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
                tickTakingOff()
            }
            FlightState.FLYING -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
                tickFlying()
            }
            FlightState.CIRCULAR_FLYING -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
                tickCircularFlying()
            }
            FlightState.WATER_HOVERING -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
                tickWaterHovering()
            }
            FlightState.GROUND_HOVERING -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
                tickGroundHovering()
            }
            FlightState.LANDING -> {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, true)
                tickLanding()
            }
        }
    }

    private fun tickPerching() {
        profile.spawnObserveTicks++
        if (profile.spawnObserveTicks >= 60) {
            transitionTo(FlightState.GROUNDED)
        }
    }

    private fun tickGrounded() {
        com.toancao.pokemonai.utils.StaminaManager.recoverStamina(profile, globalTick)
        NormalFlightAI.tickGrounded(pokemon, profile, globalTick)

        // Chỉ reset bounceCount và recoveryRate khi stamina hồi phục đầy 100%
        if (profile.currentStamina >= profile.config.maxFlightTicks) {
            profile.bounceCount = 0
            com.toancao.pokemonai.utils.StaminaManager.resetRecoveryRate(profile)
        }

        val nextState = FlightTransitionRules.evaluateGrounded(pokemon, profile, globalTick)
        if (nextState != null) {
            transitionTo(nextState)
        }
    }

    private fun tickTakingOff() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        val done = NormalFlightAI.tickTakingOff(pokemon, profile, globalTick)
        
        // Timeout 5 giây (100 ticks) hoặc đã đạt độ cao thì chuyển sang bay thẳng
        if (done || profile.ticksInCurrentState > 100) {
            profile.flightCount++
            transitionTo(FlightState.FLYING)
            return
        }

        // Nếu hết thể lực giữa lúc cất cánh
        if (profile.currentStamina <= 0) {
            transitionTo(FlightState.LANDING)
            return
        }

        // Engine tự dừng khi bị đánh rớt, vào nước...
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.GROUNDED)
            return
        }
    }

    private fun tickFlying() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        NormalFlightAI.tickFlying(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout → chuyển về GROUNDED
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.GROUNDED)
            return
        }

        val nextState = FlightTransitionRules.evaluateFlying(pokemon, profile, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickWaterHovering() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        NormalFlightAI.tickWaterHovering(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout → chuyển về GROUNDED
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.GROUNDED)
            return
        }

        val nextState = FlightTransitionRules.evaluateFlying(pokemon, profile, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickGroundHovering() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        NormalFlightAI.tickGroundHovering(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout → chuyển về GROUNDED
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.GROUNDED)
            return
        }

        val nextState = FlightTransitionRules.evaluateGroundHovering(pokemon, profile, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickCircularFlying() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        NormalFlightAI.tickCircularFlying(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout → chuyển về GROUNDED
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.GROUNDED)
            return
        }

        val nextState = FlightTransitionRules.evaluateCircularFlying(pokemon, profile, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickLanding() {
        val result = NormalFlightAI.tickLanding(pokemon, profile, globalTick)
        if (result == NormalFlightAI.LandingResult.DONE) {
            transitionTo(FlightState.GROUNDED)
        } else if (result == NormalFlightAI.LandingResult.BOUNCE) {
            profile.bounceCount++
            com.toancao.pokemonai.utils.StaminaManager.pumpStaminaOnBounce(profile)

            transitionTo(FlightState.TAKING_OFF)
            // Tạm thời cộng thêm độ cao ưu tiên để nó chịu khó vút lên trên trước khi lướt ngang
            profile.currentPreferredHeight += 10.0
        }
    }

    fun transitionTo(newState: FlightState) {
        state = newState
        profile.ticksInCurrentState = 0
        profile.circularFlightCenter = null

        if (newState == FlightState.TAKING_OFF) {
            profile.refreshPreferredHeight()
            profile.verticalVelocity = 0.0
            profile.currentYaw = kotlin.random.Random.nextDouble() * 360.0
        }
    }


    fun getState(): FlightState = state
    fun isAlive(): Boolean = mob.isAlive && !mob.isRemoved && mob.level() != null

    private fun updateDebugDisplay() {
        if (com.toancao.pokemonai.utils.DebugUtils.enabled) {
            val level = mob.level() as? net.minecraft.server.level.ServerLevel ?: return
            var display = profile.debugTextDisplay
            if (display == null || display.isRemoved) {
                display = net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level)
                display.setPos(mob.x, mob.y + mob.bbHeight + 0.8, mob.z)
                display.billboardConstraints = net.minecraft.world.entity.Display.BillboardConstraints.CENTER
                display.backgroundColor = 0x44000000 // Đen mờ
                level.addFreshEntity(display)
                profile.debugTextDisplay = display
            }
            display.teleportTo(mob.x, mob.y + mob.bbHeight + 0.8, mob.z)
            val text = net.minecraft.network.chat.Component.literal(
                "§eState: §f${state.name}\n" +
                "§aStamina: §f${String.format("%.1f", profile.currentStamina)} / ${profile.config.maxFlightTicks}\n" +
                "§bBounce: §f${profile.bounceCount} / 3"
            )
            display.text = text
        } else {
            profile.debugTextDisplay?.discard()
            profile.debugTextDisplay = null
        }
    }
}
