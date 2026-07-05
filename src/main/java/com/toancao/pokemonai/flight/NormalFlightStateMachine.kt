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
    
    var lastPlayerSeenTick: Int = 0
    private var idleTimer = 0
    var nearestPlayer: Player? = null
    var nearestDistance: Double = Double.MAX_VALUE
    var hasPlayerInRadius: Boolean = false
    private var lastScanTick = 0

    fun isFlying(): Boolean {
        return state == FlightState.TAKING_OFF || state == FlightState.FLYING || 
               state == FlightState.CIRCULAR_FLYING || state == FlightState.WATER_HOVERING || 
               state == FlightState.GROUND_HOVERING || state == FlightState.LANDING
    }

    fun canBeRemovedSafely(unloadDelay: Int): Boolean {
        if (isFlying()) return false
        return idleTimer > unloadDelay
    }

    fun markPlayerSeen() {
        lastPlayerSeenTick = globalTick
        idleTimer = 0
    }
    fun cleanup() {
        profile.debugTextDisplay?.discard()
        profile.debugTextDisplay = null
    }

    private fun updatePlayerContext() {
        // Chỉ scan mỗi 20 ticks
        if (globalTick - lastScanTick < 20 && globalTick != 1) return 
        lastScanTick = globalTick
        
        val level = mob.level() ?: return
        val r = 128.0
        val box = net.minecraft.world.phys.AABB(mob.x - r, mob.y - r, mob.z - r, mob.x + r, mob.y + r, mob.z + r)
        val players = level.getEntitiesOfClass(Player::class.java, box)
        
        if (players.isEmpty()) {
            nearestPlayer = null
            nearestDistance = Double.MAX_VALUE
            hasPlayerInRadius = false
        } else {
            nearestPlayer = players.minByOrNull { it.distanceTo(mob) }
            nearestDistance = nearestPlayer!!.distanceTo(mob).toDouble()
            hasPlayerInRadius = nearestDistance <= profile.config.activationRadius
        }
        
        if (hasPlayerInRadius) {
            markPlayerSeen()
        }
    }

    fun tick() {
        if (!isAlive()) {
            cleanup()
            return
        }
        globalTick++
        profile.ticksInCurrentState++
        
        updatePlayerContext()
        
        if (isFlying() && state != FlightState.LANDING) {
            if (profile.config.dropOnHit && mob.hurtTime > 0) {
                transitionTo(FlightState.LANDING)
                return
            }
        }

        if (!isFlying()) {
            if (globalTick - lastPlayerSeenTick > 100) {
                idleTimer++
            }
        } else {
            idleTimer = 0
        }

        updateDebugDisplay()

        val isEligible = com.toancao.pokemonai.utils.AIFilter.isEligible(pokemon)
        
        // Nếu không đủ điều kiện (đang ngủ, bị thu phục, v.v.)
        if (!isEligible) {
            // Khẩn cấp: Nếu đang đánh nhau, có mục tiêu, hoặc ĐANG NGỦ (bất tỉnh), phải NHẢ AI NGAY LẬP TỨC!
            // Không được dùng hạ cánh từ từ (LANDING) vì nó sẽ ghi đè trạng thái ngủ hoặc di chuyển tấn công.
            if (pokemon.target != null || pokemon.battleId != null || pokemon.isSleeping) {
                com.toancao.pokemonai.flight.engine.FlightEngine.stopFlight(pokemon)
                transitionTo(FlightState.GROUNDED)
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
                return
            }

            if (isFlying()) {
                // Nếu đang bay mà lăn ra ngủ, ép nó phải hạ cánh trước
                transitionTo(FlightState.LANDING)
            } else if (state == FlightState.LANDING) {
                // Đang trong quá trình hạ cánh do không đủ điều kiện -> VẪN TIẾP TỤC cho đến khi chạm đất
            } else {
                // Nếu đã ở dưới đất (GROUNDED, PERCHING), thả AI về cho Cobblemon
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

        val nextState = FlightTransitionRules.evaluateGrounded(this, globalTick)
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
            if (profile.config.hoverOnly) {
                transitionTo(FlightState.GROUND_HOVERING)
            } else {
                transitionTo(FlightState.FLYING)
            }
            return
        }

        // Nếu hết thể lực giữa lúc cất cánh
        if (profile.currentStamina <= 0) {
            transitionTo(FlightState.LANDING)
            return
        }

        // Engine tự dừng khi bị đánh rớt, vào nước... -> vẫn phải hạ cánh qua LANDING, không được nhảy thẳng xuống GROUNDED vì Pokemon còn ở trên không
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.LANDING)
            return
        }
    }

    private fun tickFlying() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        NormalFlightAI.tickFlying(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout -> vẫn phải hạ cánh qua LANDING, không được nhảy thẳng xuống GROUNDED vì Pokemon còn ở trên không
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.LANDING)
            return
        }

        val nextState = FlightTransitionRules.evaluateFlying(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickWaterHovering() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        NormalFlightAI.tickWaterHovering(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout -> vẫn phải hạ cánh qua LANDING, không được nhảy thẳng xuống GROUNDED vì Pokemon còn ở trên không
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.LANDING)
            return
        }

        val nextState = FlightTransitionRules.evaluateWaterHovering(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickGroundHovering() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        NormalFlightAI.tickGroundHovering(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout -> vẫn phải hạ cánh qua LANDING, không được nhảy thẳng xuống GROUNDED vì Pokemon còn ở trên không
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.LANDING)
            return
        }

        val nextState = FlightTransitionRules.evaluateGroundHovering(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickCircularFlying() {
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        NormalFlightAI.tickCircularFlying(pokemon, profile, globalTick)

        // Engine tự dừng khi bị đánh, vào nước, hoặc timeout -> vẫn phải hạ cánh qua LANDING, không được nhảy thẳng xuống GROUNDED vì Pokemon còn ở trên không
        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.LANDING)
            return
        }

        val nextState = FlightTransitionRules.evaluateCircularFlying(this, globalTick)
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
            
            var yawChosen = false
            if (nearestPlayer != null) {
                // Nếu người chơi ở trong phạm vi (gần), tỉ lệ 30%. Nếu ngoài phạm vi (xa), tỉ lệ 50%
                val chance = if (hasPlayerInRadius) 0.3 else 0.5
                if (kotlin.random.Random.nextDouble() < chance) {
                    val dx = nearestPlayer!!.x - mob.x
                    val dz = nearestPlayer!!.z - mob.z
                    val exactYaw = Math.toDegrees(kotlin.math.atan2(dz, dx)) - 90.0
                    // Sai số ngẫu nhiên +- 15 độ
                    val offset = (kotlin.random.Random.nextDouble() - 0.5) * 30.0
                    profile.currentYaw = (exactYaw + offset) % 360.0
                    yawChosen = true
                }
            }
            if (!yawChosen) {
                profile.currentYaw = kotlin.random.Random.nextDouble() * 360.0
            }
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