package com.toancao.pokemonai.flight.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.CustomFlightManager
import com.toancao.pokemonai.flight.CustomFlightProfile
import com.toancao.pokemonai.flight.FlightConfig

import com.toancao.pokemonai.flight.FlightHelpers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3
import java.util.UUID

object FlightEngine {

    private val activeSessions = mutableMapOf<UUID, FlightSession>()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            val snapshot = activeSessions.entries.toList()
            val toRemove = mutableListOf<Pair<UUID, FlightSession>>()
            for ((uuid, session) in snapshot) {
                if (activeSessions[uuid] !== session) continue
                if (!session.isAlive() || session.state == InternalFlightState.DONE) {
                    toRemove.add(uuid to session)
                } else {
                    tickSession(session)
                    if (activeSessions[uuid] === session && session.state == InternalFlightState.DONE) {
                        toRemove.add(uuid to session)
                    }
                }
            }
            toRemove.forEach { (uuid, session) ->
                if (activeSessions[uuid] === session) {
                    activeSessions.remove(uuid)
                    com.toancao.pokemonai.api.PokemonAIEvents.FLIGHT_END.invoker().onFlightEnd(session.pokemon)
                }
            }
        }
    }

    fun flyTo(
        pokemon: PokemonEntity,
        target: Vec3,
        hover: Boolean = false,
        config: FlightConfig = FlightConfig()
    ): Boolean {
        return flyCommand(pokemon, target, hover, config, FlightControlOwner.DIRECTED)
    }

    /**
     * Phase 3: autonomous AI dùng đường riêng, không ghi đè session DIRECTED.
     * Chỉ internal/package để public API directed giữ semantics ưu tiên.
     */
    fun flyAutonomouslyTo(
        pokemon: PokemonEntity,
        target: Vec3,
        hover: Boolean = false,
        config: FlightConfig = FlightConfig()
    ): Boolean {
        return flyCommand(pokemon, target, hover, config, FlightControlOwner.AUTONOMOUS)
    }

    private fun flyCommand(
        pokemon: PokemonEntity,
        target: Vec3,
        hover: Boolean,
        config: FlightConfig,
        owner: FlightControlOwner
    ): Boolean {
        val existingSession = activeSessions[pokemon.uuid]
        if (existingSession != null) {
            if (owner == FlightControlOwner.AUTONOMOUS && existingSession.owner == FlightControlOwner.DIRECTED) {
                return false
            }
            existingSession.target = target
            existingSession.hover = hover
            existingSession.config = config
            existingSession.owner = owner
            existingSession.state = InternalFlightState.FLYING
        } else {
            if (owner == FlightControlOwner.DIRECTED) {
                val allow = com.toancao.pokemonai.api.PokemonAIEvents.FLIGHT_START.invoker().onFlightStart(pokemon, target, hover)
                if (!allow) return false
            }
            val mob = pokemon as net.minecraft.world.entity.Mob
            mob.deltaMovement = Vec3.ZERO
            val session = FlightSession(pokemon, target, hover, config)
            session.owner = owner
            activeSessions[pokemon.uuid] = session
        }
        return true
    }

    fun land(
        pokemon: PokemonEntity,
        config: FlightConfig = FlightConfig(),
        avoidWater: Boolean = true,
        owner: FlightControlOwner = FlightControlOwner.AUTONOMOUS,
        resetRetries: Boolean = true
    ) {
        val existingSession = activeSessions[pokemon.uuid]
        if (existingSession != null && owner == FlightControlOwner.AUTONOMOUS &&
            existingSession.owner == FlightControlOwner.DIRECTED
        ) {
            return
        }

        val mob = pokemon as net.minecraft.world.entity.Mob
        val level = mob.level() ?: return

        val safeSite = try {
            com.toancao.pokemonai.flight.navigation.LandingSiteFinder.findLandingSite(pokemon)
        } catch (_: Exception) {
            null
        }
        val target: Vec3
        val siteFound = safeSite != null
        if (siteFound) {
            target = safeSite
        } else if (!avoidWater) {
            val yaw = Math.toRadians(mob.yRot.toDouble())
            val dirX = -kotlin.math.sin(yaw)
            val dirZ = kotlin.math.cos(yaw)
            val targetX = mob.x + dirX * 15.0
            val targetZ = mob.z + dirZ * 15.0
            val groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetX.toInt(), targetZ.toInt()).toDouble()
            target = Vec3(targetX, groundY, targetZ)
        } else {
            if (existingSession != null) {
                existingSession.needsBounce = true
            }
            return
        }
        
        if (existingSession != null) {
            existingSession.target = target
            existingSession.hover = false
            existingSession.config = config
            existingSession.owner = owner
            existingSession.state = InternalFlightState.FLYING
            existingSession.isSearchingLand = avoidWater
            existingSession.needsBounce = false
            if (resetRetries) existingSession.landRetries = 0
        } else {
            mob.deltaMovement = Vec3.ZERO
            val newSession = FlightSession(pokemon, target, false, config)
            newSession.owner = owner
            newSession.isSearchingLand = avoidWater
            if (!resetRetries) newSession.landRetries = 1
            activeSessions[pokemon.uuid] = newSession
        }
    }

    /** Dọn session khi entity unload mà không phát movement lên entity đã unload. */
    fun removeForUnload(uuid: java.util.UUID) {
        activeSessions.remove(uuid)
    }

    fun isDirectedFlight(pokemon: PokemonEntity): Boolean =
        activeSessions[pokemon.uuid]?.owner == FlightControlOwner.DIRECTED

    fun stopFlight(pokemon: PokemonEntity) {
        val session = activeSessions.remove(pokemon.uuid)
        if (session != null) {
            com.toancao.pokemonai.api.PokemonAIEvents.FLIGHT_END.invoker().onFlightEnd(pokemon)
        }
        FlightHelpers.terminateFlight(pokemon)
    }

    fun hasActiveFlight(pokemon: PokemonEntity): Boolean = activeSessions.containsKey(pokemon.uuid)

    fun sessionCount(): Int = activeSessions.size

    fun sessionOwner(pokemon: PokemonEntity): FlightControlOwner? = activeSessions[pokemon.uuid]?.owner

    fun suspendForRiding(pokemon: PokemonEntity) {
        val session = activeSessions[pokemon.uuid]
        FlightHelpers.terminateFlight(pokemon)
        if (session != null) {
            session.state = InternalFlightState.DONE
        }
    }

    fun needsBounce(pokemon: PokemonEntity): Boolean = activeSessions[pokemon.uuid]?.needsBounce == true

    private fun tickSession(session: FlightSession) {
        val mob = session.pokemon as net.minecraft.world.entity.Mob
        if (mob.isVehicle) {
            suspendForRiding(session.pokemon)
            return
        }
        val p = session.config
        session.ticksInCurrentState++

        if (p.dropOnHit && mob.hurtTime > 0) {
            land(session.pokemon, p, avoidWater = false, owner = session.owner)
            return
        }

        val waterStatus = FlightHelpers.checkWaterStatus(mob)
        if (waterStatus == FlightHelpers.WaterStatus.SUBMERGED && p.stopWhenFullySubmerged) {
            terminateSession(session)
            return
        }
        if (waterStatus == FlightHelpers.WaterStatus.SURFACE && p.dropOnWaterSurface) {
            terminateSession(session)
            return
        }

        when (session.state) {
            InternalFlightState.FLYING -> tickFlying(mob, session)
            InternalFlightState.ARRIVED_HOVER -> tickHovering(mob, session)
            InternalFlightState.FALLING -> tickFalling(mob, session)
            InternalFlightState.DONE -> { /* đã bị đánh dấu remove */ }
        }
    }

    private fun tickFlying(mob: net.minecraft.world.entity.Mob, session: FlightSession) {
        val level = mob.level()
        val posBelow = mob.blockPosition().below()
        val stateBelow = level.getBlockState(posBelow)
        
        val isPhysicallyOnGround = mob.onGround() || 
            (!stateBelow.isAir && !stateBelow.getCollisionShape(level, posBelow).isEmpty)

        if (session.isSearchingLand && (isPhysicallyOnGround || mob.isInWater || mob.isUnderWater)) {
            terminateSession(session)
            return
        }

        if (session.isSearchingLand && session.target.y <= mob.y) {
            var isWaterBelow = false
            var foundGround = false
            val level = mob.level()
            
            for (i in 1..5) {
                val checkY = mob.y.toInt() - i
                val pos = net.minecraft.core.BlockPos(mob.x.toInt(), checkY, mob.z.toInt())
                val state = level.getBlockState(pos)
                if (!state.isAir) {
                    foundGround = true
                    if (!state.fluidState.isEmpty) {
                        isWaterBelow = true
                    }
                    break
                }
            }

            if (foundGround && isWaterBelow) {
                session.needsBounce = true
                return
            }
        }

        val p = session.config
        val target = session.target
        val dx = target.x - mob.x
        val dy = target.y - mob.y
        val dz = target.z - mob.z

        FlightHelpers.applyFlyingPhysics(session.pokemon)

        val horizDist = kotlin.math.sqrt(dx * dx + dz * dz)
        val vertDist = kotlin.math.abs(dy)

        if (horizDist < p.arriveThresholdHoriz && vertDist < p.arriveThresholdVert) {
            if (session.isSearchingLand) {
                session.landRetries++
                if (session.landRetries >= 3) {
                    session.needsBounce = true
                    session.isSearchingLand = false
                    return
                }
                land(session.pokemon, p, true, owner = session.owner, resetRetries = false)
                return
            }

            if (session.hover) {
                mob.deltaMovement = Vec3.ZERO
                session.state = InternalFlightState.ARRIVED_HOVER
            } else {
                mob.isNoGravity = false
                session.state = InternalFlightState.FALLING
            }
            return
        }

        var rawVec = Vec3(dx, dy, dz)
        if (p.obstacleAvoidance) {
            rawVec = FlightHelpers.checkObstacleAhead(mob, rawVec, p.obstacleCheckRange, p.obstacleClimbAmount)
        }

        val speed = resolveSpeed(mob, session)
        val targetVelocity = rawVec.normalize().scale(speed)
        mob.deltaMovement = mob.deltaMovement.lerp(targetVelocity, p.lerpFactor)
        mob.hasImpulse = true
        mob.move(MoverType.SELF, mob.deltaMovement)
        FlightHelpers.syncRotationFromVelocity(mob)
    }

    private fun tickHovering(mob: net.minecraft.world.entity.Mob, session: FlightSession) {
        FlightHelpers.applyFlyingPhysics(session.pokemon)
        
        val dxSnap = (session.target.x - mob.x) * 0.1
        val dySnap = (session.target.y - mob.y) * 0.1
        val dzSnap = (session.target.z - mob.z) * 0.1
        
        val targetVelocity = Vec3(dxSnap, dySnap, dzSnap)
        mob.deltaMovement = mob.deltaMovement.lerp(targetVelocity, 0.5)
        mob.hasImpulse = true
        mob.move(MoverType.SELF, mob.deltaMovement)
    }

    private fun tickFalling(mob: net.minecraft.world.entity.Mob, session: FlightSession) {
        val level = mob.level()
        val posBelow = mob.blockPosition().below()
        val stateBelow = level.getBlockState(posBelow)
        
        val isPhysicallyOnGround = mob.onGround() || 
            (!stateBelow.isAir && !stateBelow.getCollisionShape(level, posBelow).isEmpty)

        if (isPhysicallyOnGround || mob.isInWater || mob.isUnderWater) {
            FlightHelpers.terminateFlight(session.pokemon)
            mob.deltaMovement = Vec3.ZERO
            session.state = InternalFlightState.DONE
        }
    }

    private fun terminateSession(session: FlightSession) {
        FlightHelpers.terminateFlight(session.pokemon)
        session.state = InternalFlightState.DONE
    }

    private fun resolveSpeed(mob: net.minecraft.world.entity.Mob, session: FlightSession): Double {
        var speed = session.config.flightSpeed
        if (session.config.speedPlayerScale) {
            val machine = CustomFlightManager.getMachine(mob.uuid)
            val dist = machine?.nearestDistance ?: Double.MAX_VALUE
            
            val ratio = if (dist >= 128.0) 1.0 else (dist / 128.0).coerceIn(0.0, 1.0)
            speed *= (1.0 - ratio * 0.7)
        }
        return speed
    }
}
