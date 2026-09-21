package com.toancao.pokemonai.flight.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.CustomFlightProfile
import com.toancao.pokemonai.flight.FlightHelpers
import com.toancao.pokemonai.flight.engine.FlightEngine
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object NormalFlightAI {

    fun tickGrounded(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        FlightHelpers.restoreAI(pokemon as net.minecraft.world.entity.Mob)
    }

    private fun easeInOutQuad(t: Double) = if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t

    fun tickTakingOff(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int): Boolean {
        val mob = pokemon as net.minecraft.world.entity.Mob
        val config = profile.config
        val takeoffDuration = config.takeoffDuration
        
        val progress = (profile.ticksInCurrentState.toDouble() / takeoffDuration).coerceIn(0.0, 1.0)
        val easedProgress = easeInOutQuad(progress) * config.takeoffAcceleration
        
        val radians = Math.toRadians(profile.currentYaw)
        val dirX = -sin(radians)
        val dirZ = cos(radians)
        val groundY = FlightHelpers.estimateGroundY(mob)
        val targetY = groundY + profile.currentPreferredHeight
        
        val currentSpeed = config.flightSpeed * (0.1 + easedProgress * 0.9)
        
        FlightEngine.flyAutonomouslyTo(
            pokemon = pokemon,
            target = Vec3(mob.x + dirX * 5.0, targetY, mob.z + dirZ * 5.0),
            hover = false,
            config = profile.config.copy(flightSpeed = currentSpeed)
        )
        
        FlightHelpers.spawnTakeoffParticles(mob.level(), mob, progress, profile.takeoffParticleStyle)
        
        return progress >= 0.95 || (mob.y - groundY) >= profile.currentPreferredHeight * 0.8
    }

    fun tickFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob

        if (tick % profile.config.directionChangeInterval == 0) {
            FlightHelpers.applyDirectionChange(profile)
        }

        val radians = Math.toRadians(profile.currentYaw)
        val dirX = -sin(radians)
        val dirZ = cos(radians)
        val groundY = FlightHelpers.estimateGroundY(mob)
        val sinSway = sin(tick * 0.02 + mob.id * 0.3) * profile.config.verticalSway
        
        var extraHeight = 0.0
        val level = mob.level()
        for (i in 1..2) {
            val checkPos = net.minecraft.core.BlockPos(mob.x.toInt(), mob.y.toInt() - i, mob.z.toInt())
            if (!level.getBlockState(checkPos).isAir) {
                extraHeight = 2.5
                break
            }
        }
        
        val targetY = groundY + profile.currentPreferredHeight + sinSway + extraHeight

        FlightEngine.flyAutonomouslyTo(
            pokemon = pokemon,
            target = Vec3(mob.x + dirX * 10.0, targetY, mob.z + dirZ * 10.0),
            hover = false,
            config = profile.config
        )
    }

    fun tickWaterHovering(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        
        if (profile.ticksInCurrentState == 1 || profile.ticksInCurrentState % 100 == 0) {
            val surfaceY = FlightHelpers.findWaterSurfaceY(mob) ?: return
            val targetY = surfaceY + 2.0

            if (profile.ticksInCurrentState == 1 || kotlin.random.Random.nextDouble() < 0.5) {
                val radians = kotlin.random.Random.nextDouble() * 2.0 * Math.PI
                val dist = kotlin.random.Random.nextDouble(2.0, 5.0)
                val dirX = kotlin.math.cos(radians) * dist
                val dirZ = kotlin.math.sin(radians) * dist
                
                val dirY = kotlin.random.Random.nextDouble(-1.5, 2.5)
                val finalTargetY = kotlin.math.max(surfaceY + 1.0, targetY + dirY)
                
                FlightEngine.flyAutonomouslyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x + dirX, finalTargetY, mob.z + dirZ),
                    hover = true,
                    config = profile.config
                )
            } else {
                FlightEngine.flyAutonomouslyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x, targetY, mob.z),
                    hover = true,
                    config = profile.config
                )
            }
        }
    }
    fun tickGroundHovering(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        
        if (profile.ticksInCurrentState == 1 || profile.ticksInCurrentState % 100 == 0) {
            val groundY = FlightHelpers.estimateGroundY(mob)
            val targetY = groundY + 3.5

            if (profile.ticksInCurrentState == 1 || kotlin.random.Random.nextDouble() < 0.7) {
                val radians = kotlin.random.Random.nextDouble() * 2.0 * Math.PI
                val dist = kotlin.random.Random.nextDouble(2.0, 5.0)
                val dirX = kotlin.math.cos(radians) * dist
                val dirZ = kotlin.math.sin(radians) * dist
                
                val dirY = kotlin.random.Random.nextDouble(-0.5, 1.5)
                val finalTargetY = kotlin.math.max(groundY + 2.0, targetY + dirY)
                
                FlightEngine.flyAutonomouslyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x + dirX, finalTargetY, mob.z + dirZ),
                    hover = true,
                    config = profile.config
                )
            } else {
                FlightEngine.flyAutonomouslyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x, targetY, mob.z),
                    hover = true,
                    config = profile.config
                )
            }
        }
    }


    fun tickCircularFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        val radius = profile.config.circularFlightRadius
        val speed = profile.config.flightSpeed

        if (profile.ticksInCurrentState <= 1 || profile.circularFlightCenter == null) {
            val groundY = FlightHelpers.estimateGroundY(mob)
            val targetY = Math.max(profile.anchorY, groundY + profile.currentPreferredHeight)
            
            profile.circularFlightCenter = Vec3(profile.anchorX, targetY, profile.anchorZ)
            profile.circularFlightAngle = Math.atan2(mob.z - profile.circularFlightCenter!!.z, mob.x - profile.circularFlightCenter!!.x)
        }

        val center = profile.circularFlightCenter!!
        
        val currentAngle = Math.atan2(mob.z - center.z, mob.x - center.x)
        
        val angularSpeed = speed / radius
        val targetAngle = currentAngle + Math.max(angularSpeed * 1.5, 0.3)
        
        val targetX = center.x + radius * cos(targetAngle)
        val targetZ = center.z + radius * sin(targetAngle)
        
        val sinSway = sin(tick * 0.02 + mob.id * 0.3) * profile.config.verticalSway
        
        var extraHeight = 0.0
        val level = mob.level()
        for (i in 1..2) {
            val checkPos = net.minecraft.core.BlockPos(mob.x.toInt(), mob.y.toInt() - i, mob.z.toInt())
            if (!level.getBlockState(checkPos).isAir) {
                extraHeight = 2.5
                break
            }
        }
        
        val targetY = center.y + sinSway + extraHeight

        FlightEngine.flyAutonomouslyTo(
            pokemon = pokemon,
            target = Vec3(targetX, targetY, targetZ),
            hover = false,
            config = profile.config
        )
    }

    enum class LandingResult { CONTINUE, DONE, BOUNCE }

    fun tickLanding(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int): LandingResult {
        val mob = pokemon as net.minecraft.world.entity.Mob
        
        if (profile.ticksInCurrentState == 1) {
            FlightEngine.land(pokemon, profile.config, avoidWater = profile.bounceCount < 3)
            return LandingResult.CONTINUE
        }

        if (FlightEngine.needsBounce(pokemon)) {
            FlightEngine.stopFlight(pokemon)
            return LandingResult.BOUNCE
        }

        if (!FlightEngine.hasActiveFlight(pokemon) || mob.onGround() || mob.isInWater || mob.isUnderWater) {
            FlightEngine.stopFlight(pokemon)
            return LandingResult.DONE
        }

        return LandingResult.CONTINUE
    }
}
