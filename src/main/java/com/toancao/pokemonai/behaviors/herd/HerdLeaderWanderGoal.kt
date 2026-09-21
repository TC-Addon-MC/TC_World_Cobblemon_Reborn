package com.toancao.pokemonai.behaviors.herd

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.HerdRole
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

class HerdLeaderWanderGoal(private val entity: PokemonEntity) : Goal() {

    private var targetPos: Vec3? = null
    private var restCooldownTicks = 0

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        val data = entity.getHerdData()
        if (!data.isInHerd || !data.isLeader) return false
        if (entity.isBattling) return false

        if (entity.target != null && entity.target!!.isAlive) return false
        if (data.isStampeding) return false

        if (restCooldownTicks > 0) {
            restCooldownTicks--
            return false
        }

        val randomPos = LandRandomPos.getPos(entity, 32, 10) ?: LandRandomPos.getPos(entity, 20, 7) ?: return false
        targetPos = randomPos
        return true
    }

    override fun canContinueToUse(): Boolean {
        if (entity.isBattling) return false
        if (entity.target != null && entity.target!!.isAlive) return false
        if (entity.getHerdData().isStampeding) return false
        return !entity.navigation.isDone && targetPos != null
    }

    override fun start() {
        targetPos?.let {
            entity.navigation.moveTo(it.x, it.y, it.z, 0.50)
        }
    }

    override fun stop() {
        targetPos = null
        restCooldownTicks = entity.random.nextInt(300, 600)
    }
}
