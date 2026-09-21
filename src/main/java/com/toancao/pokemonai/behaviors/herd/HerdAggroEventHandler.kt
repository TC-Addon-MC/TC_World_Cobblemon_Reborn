package com.toancao.pokemonai.behaviors.herd

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.behaviors.combat.HerdSharedAggroGoal
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB

object HerdAggroEventHandler {

    fun register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, baseDamage, damageTaken, blocked ->
            if (entity is PokemonEntity && !entity.level().isClientSide) {
                val data = entity.getHerdData()
                if (data.isInHerd) {
                    val attacker = source.entity
                    if (attacker is net.minecraft.world.entity.player.Player && attacker.isAlive) {
                        val level = entity.level() as? ServerLevel ?: return@register
                        val herdId = data.herdId ?: return@register
                        val dirVec = attacker.position().subtract(entity.position()).normalize()
                        val searchBox = AABB.ofSize(entity.position(), 128.0, 64.0, 128.0)

                        HerdSharedAggroGoal.triggerHerdStampedeDirection(level, herdId, entity.position(), dirVec, searchBox)
                    }
                }
            }
        }

        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { event ->
            val poke = event.pokemonEntity
            val data = poke.getHerdData()
            if (data.isInHerd && !event.captureResult.isSuccessfulCapture) {
                val thrower = event.thrower
                if (thrower is net.minecraft.world.entity.player.Player && thrower.isAlive) {
                    val level = poke.level() as? ServerLevel ?: return@subscribe
                    val herdId = data.herdId ?: return@subscribe
                    val dirVec = thrower.position().subtract(poke.position()).normalize()
                    val searchBox = AABB.ofSize(poke.position(), 128.0, 64.0, 128.0)

                    HerdSharedAggroGoal.triggerHerdStampedeDirection(level, herdId, poke.position(), dirVec, searchBox)
                }
            }
        }
    }
}
