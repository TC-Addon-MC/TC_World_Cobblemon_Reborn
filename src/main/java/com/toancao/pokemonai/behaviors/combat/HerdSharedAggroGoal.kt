package com.toancao.pokemonai.behaviors.combat

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.HerdRole
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.spawner.hierarchy.setHerdData
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class HerdSharedAggroGoal(private val entity: PokemonEntity) : Goal() {

    private var cooldown = 0

    override fun canUse(): Boolean {
        if (entity.isBattling) return false
        val data = entity.getHerdData()
        if (!data.isInHerd) return false

        if (cooldown > 0) {
            cooldown--
            return false
        }

        val herdId = data.herdId ?: return false
        val level = entity.level() as? ServerLevel ?: return false
        val box = AABB.ofSize(entity.position(), 80.0, 32.0, 80.0)

        // Quét xem trong đàn có con nào đang có mục tiêu là NGƯỜI CHƠI
        val pack = level.getEntitiesOfClass(PokemonEntity::class.java, box) {
            it.getHerdData().herdId == herdId && it.target is net.minecraft.world.entity.player.Player && it.target!!.isAlive
        }

        val angryMember = pack.firstOrNull() ?: return false
        val targetEntity = angryMember.target ?: return false

        // Nếu đàn chưa phát động Stampede -> Khóa HƯỚNG và phát động Đại Xung Phong cho TOÀN BỘ ĐÀN!
        if (!data.isStampeding) {
            val dirVec = targetEntity.position().subtract(entity.position()).normalize()
            triggerHerdStampedeDirection(level, herdId, entity.position(), dirVec, box)
            cooldown = 200
            return true
        }

        return false
    }

    companion object {
        /**
         * Kích hoạt Đại Xung Phong:
         * - Con đầu đàn (Alpha Leader) LAO LÊN TRƯỚC TIÊN PHONG (Delay = 0).
         * - Các con đi sau nối đuôi chạy theo hướng của con đầu đàn (Delay tăng dần theo thứ bậc).
         * - Trong lúc này mọi tính năng bầy đàn nhàn rỗi đều bị khóa.
         */
        fun triggerHerdStampedeDirection(level: ServerLevel, herdId: java.util.UUID, centerPos: Vec3, direction: Vec3, searchBox: AABB) {
            val normDir = Vec3(direction.x, 0.0, direction.z).normalize()
            val dirX = if (normDir.lengthSqr() > 0.001) normDir.x else 1.0
            val dirZ = if (normDir.lengthSqr() > 0.001) normDir.z else 0.0

            val allMembers = level.getEntitiesOfClass(PokemonEntity::class.java, searchBox) {
                it.getHerdData().herdId == herdId && it.isAlive
            }

            for (member in allMembers) {
                val mData = member.getHerdData()
                mData.isStampeding = true
                mData.stampedeTicksRemaining = 200 // ~10 giây chạy đầm chắc

                mData.stampedeDirX = dirX
                mData.stampedeDirZ = dirZ

                if (mData.isLeader) {
                    // 1. Con đầu đàn phi lên trước tiên phong ngay lập tức!
                    mData.stampedeDelayTicks = 0
                } else {
                    // 2. Toàn bộ đàn em nối đuôi nhau chạy theo sau
                    mData.stampedeDelayTicks = 12 + (mData.formationIndex * 2).coerceAtMost(30)
                }
                member.setHerdData(mData)
            }

            // Hiệu ứng âm thanh gầm vang báo động toàn bầy
            level.playSound(null, net.minecraft.core.BlockPos.containing(centerPos), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 1.8f, 0.9f)
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, centerPos.x, centerPos.y + 1.5, centerPos.z, 15, 1.0, 0.5, 1.0, 0.1)
        }

        /**
         * Khi con đầu đàn rời khỏi trạng thái phẫn nộ (hết giờ, tông trúng đá, v.v.):
         * Toàn bộ các con còn lại cũng lập tức dừng phẫn nộ và bật lại tính năng bầy đàn bình thường!
         */
        fun stopHerdStampede(level: ServerLevel, herdId: java.util.UUID, searchBox: AABB) {
            val allMembers = level.getEntitiesOfClass(PokemonEntity::class.java, searchBox) {
                it.getHerdData().herdId == herdId && it.isAlive
            }

            for (member in allMembers) {
                val mData = member.getHerdData()
                if (mData.isStampeding) {
                    mData.isStampeding = false
                    mData.stampedeDelayTicks = 0
                    mData.stampedeTicksRemaining = 0
                    member.setHerdData(mData)
                    member.navigation.stop()
                    level.sendParticles(ParticleTypes.SMOKE, member.x, member.y + 1.0, member.z, 3, 0.2, 0.2, 0.2, 0.01)
                }
            }
        }
    }
}
