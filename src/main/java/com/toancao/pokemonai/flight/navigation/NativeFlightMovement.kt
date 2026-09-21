package com.toancao.pokemonai.flight.navigation

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 4 (đợt 2): adapter nhỏ bọc movement public của Cobblemon.
 * Không ghi deltaMovement, không gọi mob.move(), không bật isNoGravity thủ công.
 * Throttle navigation.moveTo để tránh recalculate path mỗi tick.
 */
object NativeFlightMovement {
    private data class TargetState(var target: Vec3, var tick: Int)

    private val lastTargets = ConcurrentHashMap<UUID, TargetState>()
    private var globalTick = 0

    fun tickGlobal() {
        globalTick++
    }

    fun moveTo(pokemon: PokemonEntity, target: Vec3, speed: Double = 1.0): Boolean {
        val mob = pokemon as? net.minecraft.world.entity.Mob ?: return false
        if (mob.level().isClientSide) return false
        if (!pokemon.isAlive || pokemon.isRemoved) return false
        if (!canFly(pokemon)) return false

        val prev = lastTargets[pokemon.uuid]
        val movedEnough = prev == null ||
            prev.target.distanceToSqr(target) > 4.0 ||
            (globalTick - prev.tick) >= 20
        if (!movedEnough && mob.navigation.isInProgress) return true

        pokemon.setFlying(true)
        return try {
            mob.navigation.moveTo(target.x, target.y, target.z, speed.coerceIn(0.1, 2.0))
            lastTargets[pokemon.uuid] = TargetState(target, globalTick)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun startBanking(
        pokemon: PokemonEntity,
        forwardBlocksPerTick: Float,
        upwardsBlocksPerTick: Float = 0f,
        rightDegreesPerTick: Float,
        durationTicks: Int = 4
    ): Boolean {
        if (!canFly(pokemon)) return false
        pokemon.setFlying(true)
        return try {
            val control = (pokemon as net.minecraft.world.entity.Mob).moveControl as? PokemonMoveControl
                ?: return false
            control.startBanking(
                forwardBlocksPerTick = forwardBlocksPerTick,
                upwardsBlocksPerTick = upwardsBlocksPerTick,
                rightDegreesPerTick = rightDegreesPerTick,
                durationTicks = durationTicks
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopBanking(pokemon: PokemonEntity) {
        try {
            val control = (pokemon as net.minecraft.world.entity.Mob).moveControl as? PokemonMoveControl
            control?.stopBanking()
        } catch (_: Exception) {
        }
    }

    /** Dừng navigation/banking do mod sở hữu, không teleport hay sửa block. */
    fun stop(pokemon: PokemonEntity, keepFlying: Boolean = false) {
        try {
            val mob = pokemon as net.minecraft.world.entity.Mob
            mob.navigation?.stop()
        } catch (_: Exception) {
        }
        stopBanking(pokemon)
        lastTargets.remove(pokemon.uuid)
        if (!keepFlying) {
            try {
                if (pokemon.couldStopFlying()) pokemon.setFlying(false)
            } catch (_: Exception) {
            }
        }
    }

    fun stopForUnload(uuid: UUID) {
        lastTargets.remove(uuid)
    }

    fun isNavigating(pokemon: PokemonEntity): Boolean {
        return try {
            (pokemon as net.minecraft.world.entity.Mob).navigation.isInProgress
        } catch (_: Exception) {
            false
        }
    }

    fun currentTarget(uuid: UUID): Vec3? = lastTargets[uuid]?.target

    fun canFly(pokemon: PokemonEntity): Boolean {
        return try {
            pokemon.canFly()
        } catch (_: Exception) {
            false
        }
    }
}
