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
        try {
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stopForUnload(pokemon.uuid)
        } catch (_: Exception) {
        }
    }

    private fun useNative(): Boolean = profile.config.useNativeNavigation

    private var stuckAnchor: net.minecraft.world.phys.Vec3? = null
    private var stuckAnchorTick = 0
    private var escapeCooldown = 0
    private var activeEscapeTarget: net.minecraft.world.phys.Vec3? = null
    private var escapeStart: net.minecraft.world.phys.Vec3? = null
    private var escapeUntilTick = 0
    var stuckEscapes = 0
        private set

    /**
     * Chỉ gọi trong TAKING_OFF/FLYING/CIRCULAR_FLYING (hover và landing chủ ý
     * đứng yên nên không kiểm tra). Giữ target thoát qua nhiều tick để logic bay
     * thường không ghi đè trước khi Pokémon thật sự rời vật cản.
     */
    private fun checkStuckInAir(): Boolean {
        if (escapeCooldown > 0) escapeCooldown--
        val cur = mob.position()
        val activeTarget = activeEscapeTarget
        if (activeTarget != null) {
            val escapedFarEnough = escapeStart?.distanceToSqr(cur)?.let { it >= 36.0 } == true
            val reachedTarget = cur.distanceToSqr(activeTarget) < 0.5625
            if (!escapedFarEnough && !reachedTarget && globalTick < escapeUntilTick) {
                moveToEscapeTarget(activeTarget)
                return true
            }
            activeEscapeTarget = null
            escapeStart = null
            stuckAnchor = cur
            stuckAnchorTick = globalTick
            if (escapedFarEnough || reachedTarget) return false
        }

        val anchor = stuckAnchor
        if (anchor == null) {
            stuckAnchor = cur
            stuckAnchorTick = globalTick
            return false
        }
        if (globalTick - stuckAnchorTick < 60) return false
        stuckAnchor = cur
        stuckAnchorTick = globalTick
        if (escapeCooldown > 0) return false
        if (anchor.distanceToSqr(cur) >= 9.0) return false

        val escape = com.toancao.pokemonai.flight.navigation.StuckEscape.chooseEscapePath(mob, profile.currentYaw)
        profile.currentYaw = escape.yaw
        stuckEscapes++
        escapeCooldown = 60

        val escapeTarget = escape.target
        activeEscapeTarget = escapeTarget
        escapeStart = cur
        escapeUntilTick = globalTick + 60
        if (useNative()) {
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stop(pokemon, keepFlying = true)
        }
        moveToEscapeTarget(escapeTarget)
        return true
    }

    private fun moveToEscapeTarget(escapeTarget: net.minecraft.world.phys.Vec3) {
        if (useNative()) {
            val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
            nav.moveTo(pokemon, escapeTarget, nativeSpeed())
        } else {
            FlightEngine.flyAutonomouslyTo(pokemon, escapeTarget, hover = false, config = profile.config)
        }
    }

    private fun updatePlayerContext() {
        if (globalTick - lastScanTick < 20 && globalTick != 1) return 
        lastScanTick = globalTick
        
        val level = mob.level() as? net.minecraft.server.level.ServerLevel ?: return
        var best: Player? = null
        var bestDist = Double.MAX_VALUE
        for (player in level.players()) {
            val d = player.distanceTo(mob).toDouble()
            if (d < bestDist) {
                bestDist = d
                best = player
            }
        }
        
        if (best == null) {
            nearestPlayer = null
            nearestDistance = Double.MAX_VALUE
            hasPlayerInRadius = false
        } else {
            nearestPlayer = best
            nearestDistance = bestDist
            hasPlayerInRadius = bestDist <= profile.config.activationRadius
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
        if (mob.isVehicle) {
            FlightEngine.suspendForRiding(pokemon)
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
        
        if (!isEligible) {
            if (pokemon.target != null || pokemon.battleId != null || pokemon.isSleeping) {
                com.toancao.pokemonai.flight.engine.FlightEngine.stopFlight(pokemon)
                com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stop(pokemon)
                transitionTo(FlightState.GROUNDED)
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
                return
            }

            if (isFlying()) {
                transitionTo(FlightState.LANDING)
            } else if (state == FlightState.LANDING) {
            } else {
                com.toancao.pokemonai.compat.CobblemonBridge.setFlyingFlag(pokemon, false)
                com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stop(pokemon)
                return
            }
        }

        if (com.toancao.pokemonai.flight.engine.FlightEngine.isDirectedFlight(pokemon)) {
            return
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
        if (checkStuckInAir()) return
        if (useNative()) {
            tickTakingOffNative()
            return
        }
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        val done = NormalFlightAI.tickTakingOff(pokemon, profile, globalTick)
        
        if (done || profile.ticksInCurrentState > 100) {
            profile.flightCount++
            if (profile.config.hoverOnly) {
                transitionTo(FlightState.GROUND_HOVERING)
            } else {
                transitionTo(FlightState.FLYING)
            }
            return
        }

        if (profile.currentStamina <= 0) {
            transitionTo(FlightState.LANDING)
            return
        }

        if (!FlightEngine.hasActiveFlight(pokemon)) {
            transitionTo(FlightState.LANDING)
            return
        }
    }

    private fun tickFlying() {
        if (checkStuckInAir()) return
        if (useNative()) {
            tickFlyingNative()
            return
        }
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        NormalFlightAI.tickFlying(pokemon, profile, globalTick)

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
        if (useNative()) {
            tickWaterHoveringNative()
            return
        }
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        NormalFlightAI.tickWaterHovering(pokemon, profile, globalTick)

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
        if (useNative()) {
            tickGroundHoveringNative()
            return
        }
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        NormalFlightAI.tickGroundHovering(pokemon, profile, globalTick)

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
        if (checkStuckInAir()) return
        if (useNative()) {
            tickCircularFlyingNative()
            return
        }
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        NormalFlightAI.tickCircularFlying(pokemon, profile, globalTick)

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
        if (useNative()) {
            tickLandingNative()
            return
        }
        val result = NormalFlightAI.tickLanding(pokemon, profile, globalTick)
        if (result == NormalFlightAI.LandingResult.DONE) {
            transitionTo(FlightState.GROUNDED)
        } else if (result == NormalFlightAI.LandingResult.BOUNCE) {
            profile.bounceCount++
            com.toancao.pokemonai.utils.StaminaManager.pumpStaminaOnBounce(profile)

            if (profile.bounceCount >= 3) {
                profile.bounceCount = 0
                if (profile.config.canGroundHover) {
                    transitionTo(FlightState.GROUND_HOVERING)
                } else {
                    profile.refreshPreferredHeight()
                    transitionTo(FlightState.FLYING)
                }
            } else {
                transitionTo(FlightState.TAKING_OFF)
            }
        }
    }


    /** Map flightSpeed cấu hình (0.1..0.5 block/tick) sang speedModifier của navigation. */
    private fun nativeSpeed(): Double = (profile.config.flightSpeed * 2.5).coerceIn(0.3, 1.5)

    /** Native thiếu guard dropOnHit/submerged của engine → kiểm tra ở đây, true nếu đã xử lý. */
    private fun checkNativeFlightInterrupt(): Boolean {
        if (profile.config.dropOnHit && mob.hurtTime > 0) {
            transitionTo(FlightState.LANDING)
            return true
        }
        if (profile.config.stopWhenFullySubmerged && mob.isUnderWater) {
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stop(pokemon)
            transitionTo(FlightState.GROUNDED)
            return true
        }
        return false
    }

    private fun clearCustomSessionIfNative() {
        if (FlightEngine.hasActiveFlight(pokemon)) {
            FlightEngine.stopFlight(pokemon)
        }
    }

    private fun tickTakingOffNative() {
        clearCustomSessionIfNative()
        if (checkNativeFlightInterrupt()) return
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
        val radians = Math.toRadians(profile.currentYaw)
        val dirX = -kotlin.math.sin(radians)
        val dirZ = kotlin.math.cos(radians)
        val groundY = FlightHelpers.estimateGroundY(mob)
        val targetY = groundY + profile.currentPreferredHeight
        val target = net.minecraft.world.phys.Vec3(mob.x + dirX * 6.0, targetY, mob.z + dirZ * 6.0)
        nav.moveTo(pokemon, target, speed = nativeSpeed())

        val progress = (profile.ticksInCurrentState.toDouble() / profile.config.takeoffDuration).coerceIn(0.0, 1.0)
        val done = progress >= 0.95 || (mob.y - groundY) >= profile.currentPreferredHeight * 0.8
        if (done || profile.ticksInCurrentState > 100) {
            profile.flightCount++
            transitionTo(if (profile.config.hoverOnly) FlightState.GROUND_HOVERING else FlightState.FLYING)
            return
        }
        if (profile.currentStamina <= 0) {
            transitionTo(FlightState.LANDING)
        }
    }

    private fun tickFlyingNative() {
        clearCustomSessionIfNative()
        if (checkNativeFlightInterrupt()) return
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        if (globalTick % profile.config.directionChangeInterval == 0) {
            FlightHelpers.applyDirectionChange(profile)
        }
        val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
        if (profile.ticksInCurrentState == 1 || globalTick % 20 == 0) {
            val radians = Math.toRadians(profile.currentYaw)
            val dirX = -kotlin.math.sin(radians)
            val dirZ = kotlin.math.cos(radians)
            val groundY = FlightHelpers.estimateGroundY(mob)
            val sway = kotlin.math.sin(globalTick * 0.02 + mob.id * 0.3) * profile.config.verticalSway
            val target = net.minecraft.world.phys.Vec3(
                mob.x + dirX * 12.0,
                groundY + profile.currentPreferredHeight + sway,
                mob.z + dirZ * 12.0
            )
            if (!nav.moveTo(pokemon, target, speed = nativeSpeed())) {
                transitionTo(FlightState.LANDING)
                return
            }
        }
        val nextState = FlightTransitionRules.evaluateFlying(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickWaterHoveringNative() {
        clearCustomSessionIfNative()
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
        if (profile.ticksInCurrentState == 1 || profile.ticksInCurrentState % 100 == 0) {
            val surfaceY = FlightHelpers.findWaterSurfaceY(mob) ?: run {
                transitionTo(FlightState.FLYING)
                return
            }
            nav.moveTo(
                pokemon,
                net.minecraft.world.phys.Vec3(mob.x, surfaceY + 2.0, mob.z),
                speed = 0.8
            )
        }
        val nextState = FlightTransitionRules.evaluateWaterHovering(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickGroundHoveringNative() {
        clearCustomSessionIfNative()
        com.toancao.pokemonai.utils.StaminaManager.consumeStaminaHover(profile)
        val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
        if (profile.ticksInCurrentState == 1 || profile.ticksInCurrentState % 100 == 0) {
            val groundY = FlightHelpers.estimateGroundY(mob)
            nav.moveTo(
                pokemon,
                net.minecraft.world.phys.Vec3(mob.x, groundY + 3.5, mob.z),
                speed = 0.8
            )
        }
        val nextState = FlightTransitionRules.evaluateGroundHovering(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickCircularFlyingNative() {
        clearCustomSessionIfNative()
        if (checkNativeFlightInterrupt()) return
        com.toancao.pokemonai.utils.StaminaManager.consumeStamina(profile)
        val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
        if (profile.ticksInCurrentState <= 1 || profile.circularFlightCenter == null) {
            val groundY = FlightHelpers.estimateGroundY(mob)
            val targetY = kotlin.math.max(profile.anchorY, groundY + profile.currentPreferredHeight)
            profile.circularFlightCenter =
                net.minecraft.world.phys.Vec3(profile.anchorX, targetY, profile.anchorZ)
        }
        val turnDegrees = ((profile.config.flightSpeed / profile.config.circularFlightRadius.coerceAtLeast(1.0)) * 57.3)
            .toFloat().coerceIn(1f, 10f)
        nav.startBanking(
            pokemon,
            forwardBlocksPerTick = profile.config.flightSpeed.toFloat().coerceIn(0.1f, 1f),
            upwardsBlocksPerTick = 0f,
            rightDegreesPerTick = turnDegrees,
            durationTicks = 4
        )
        val nextState = FlightTransitionRules.evaluateCircularFlying(this, globalTick)
        if (nextState != null) {
            profile.verticalVelocity = 0.0
            transitionTo(nextState)
        }
    }

    private fun tickLandingNative() {
        clearCustomSessionIfNative()
        val nav = com.toancao.pokemonai.flight.navigation.NativeFlightMovement
        if (profile.ticksInCurrentState == 1) {
            val site = try {
                com.toancao.pokemonai.flight.navigation.LandingSiteFinder.findLandingSite(pokemon)
            } catch (_: Exception) {
                null
            }
            if (site == null) {
                onNativeLandingBounce()
                return
            }
            nav.moveTo(pokemon, site, speed = nativeSpeed())
        }
        if (mob.onGround()) {
            nav.stop(pokemon)
            transitionTo(FlightState.GROUNDED)
            return
        }
        val target = navTargetOrNull()
        if (target != null && mob.position().distanceToSqr(target) < 4.0) {
            nav.stop(pokemon)
            transitionTo(FlightState.GROUNDED)
            return
        }
        if (profile.ticksInCurrentState > 200) {
            onNativeLandingBounce()
        }
    }

    private fun navTargetOrNull(): net.minecraft.world.phys.Vec3? {
        return try {
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.currentTarget(pokemon.uuid)
        } catch (_: Exception) {
            null
        }
    }

    private fun onNativeLandingBounce() {
        com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stop(pokemon, keepFlying = true)
        profile.bounceCount++
        com.toancao.pokemonai.utils.StaminaManager.pumpStaminaOnBounce(profile)
        if (profile.bounceCount >= 3) {
            profile.bounceCount = 0
            if (profile.config.canGroundHover) {
                transitionTo(FlightState.GROUND_HOVERING)
            } else {
                profile.refreshPreferredHeight()
                transitionTo(FlightState.FLYING)
            }
        } else {
            transitionTo(FlightState.TAKING_OFF)
        }
    }

    fun transitionTo(newState: FlightState) {
        if (state == FlightState.CIRCULAR_FLYING && newState != FlightState.CIRCULAR_FLYING) {
            com.toancao.pokemonai.flight.navigation.NativeFlightMovement.stopBanking(pokemon)
        }
        state = newState
        profile.ticksInCurrentState = 0
        profile.circularFlightCenter = null
        stuckAnchor = if (
            newState == FlightState.TAKING_OFF ||
            newState == FlightState.FLYING ||
            newState == FlightState.CIRCULAR_FLYING
        ) mob.position() else null
        stuckAnchorTick = globalTick
        activeEscapeTarget = null
        escapeStart = null
        escapeUntilTick = 0

        if (newState == FlightState.TAKING_OFF) {
            profile.refreshPreferredHeight()
            profile.verticalVelocity = 0.0
            
            var yawChosen = false
            if (nearestPlayer != null) {
                val chance = if (hasPlayerInRadius) 0.3 else 0.5
                if (kotlin.random.Random.nextDouble() < chance) {
                    val dx = nearestPlayer!!.x - mob.x
                    val dz = nearestPlayer!!.z - mob.z
                    val exactYaw = Math.toDegrees(kotlin.math.atan2(dz, dx)) - 90.0
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
                display.backgroundColor = 0x44000000
                level.addFreshEntity(display)
                profile.debugTextDisplay = display
            }
            display.teleportTo(mob.x, mob.y + mob.bbHeight + 0.8, mob.z)
            val owner = try {
                FlightEngine.sessionOwner(pokemon)?.name
                    ?: if (useNative() && com.toancao.pokemonai.flight.navigation.NativeFlightMovement.isNavigating(pokemon)) "NATIVE" else "none"
            } catch (_: Exception) {
                "?"
            }
            val mode = if (useNative()) "native" else "custom"
            val text = net.minecraft.network.chat.Component.literal(
                "§eState: §f${state.name} ($mode)\n" +
                "§aStamina: §f${String.format("%.1f", profile.currentStamina)} / ${profile.config.maxFlightTicks}\n" +
                "§bBounce: §f${profile.bounceCount} / 3 §7owner=$owner\n" +
                "§7Stuck escapes: §f$stuckEscapes"
            )
            display.text = text
        } else {
            profile.debugTextDisplay?.discard()
            profile.debugTextDisplay = null
        }
    }
}
