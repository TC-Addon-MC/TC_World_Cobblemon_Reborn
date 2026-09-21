package com.toancao.pokemonai.behaviors.herd

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.HerdRole
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.spawner.hierarchy.setHerdData
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.AABB

class HerdLeadershipSuccessionGoal(private val entity: PokemonEntity) : Goal() {

    private var checkCooldown = 40

    override fun canUse(): Boolean {
        if (entity.isBattling) return false
        val data = entity.getHerdData()
        if (!data.isInHerd || data.isLeader || data.isNativeHerd) return false

        if (--checkCooldown > 0) return false
        checkCooldown = 40

        val level = entity.level() as? ServerLevel ?: return false
        val herdId = data.herdId ?: return false

        val box = AABB.ofSize(entity.position(), 128.0, 64.0, 128.0)
        val packMembers = level.getEntitiesOfClass(PokemonEntity::class.java, box) {
            it.getHerdData().herdId == herdId && it.isAlive
        }

        val hasLivingLeader = packMembers.any { it.getHerdData().isLeader }
        if (hasLivingLeader) return false

        val successor = packMembers.maxWithOrNull(
            compareBy<PokemonEntity> { it.pokemon.isAlpha }
                .thenBy { it.pokemon.level }
                .thenBy { it.pokemon.scaleModifier }
                .thenBy { it.uuid.toString() }
        )
        return successor == entity
    }

    override fun start() {
        val level = entity.level() as? ServerLevel ?: return
        val data = entity.getHerdData()
        val herdId = data.herdId ?: return

        data.role = HerdRole.LEADER
        data.leaderUUID = entity.uuid
        data.formationIndex = 0
        entity.setHerdData(data)

        entity.customName = Component.literal("§6👑 [ĐẦU ĐÀN]")
        entity.isCustomNameVisible = false

        entity.attributes.getInstance(Attributes.SCALE)?.baseValue = 1.25

        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, entity.x, entity.y + 1.2, entity.z, 30, 0.5, 0.5, 0.5, 0.1)
        level.playSound(null, entity.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 1.0f, 1.0f)
        level.playSound(null, entity.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 1.5f, 0.8f)

        val box = AABB.ofSize(entity.position(), 128.0, 64.0, 128.0)
        val otherMembers = level.getEntitiesOfClass(PokemonEntity::class.java, box) {
            it != entity && it.getHerdData().herdId == herdId && it.isAlive
        }

        for (member in otherMembers) {
            val mData = member.getHerdData()
            mData.leaderUUID = entity.uuid
            member.setHerdData(mData)
        }
    }
}
