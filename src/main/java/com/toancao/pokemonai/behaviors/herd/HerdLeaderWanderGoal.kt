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

        // Không đi lang thang khi đang chiến đấu hoặc đàn đang đại xung phong
        if (entity.target != null && entity.target!!.isAlive) return false
        if (data.isStampeding) return false

        if (restCooldownTicks > 0) {
            restCooldownTicks--
            return false
        }

        // Mở rộng bán kính tìm bãi cỏ mới lên 32 - 40 block
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
        // Khi đến nơi, nghỉ từ 15 đến 30 giây để toàn bộ đàn có thời gian tản ra gặm cỏ thoải mái
        restCooldownTicks = entity.random.nextInt(300, 600)
    }
}
