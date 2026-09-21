package com.toancao.pokemonai.behaviors.herd

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.AABB
import java.util.*

/**
 * Hành vi thi đấu va sừng giao hữu được ĐỒNG BỘ HÓA 100% GIỮA 2 CÁ THỂ:
 * - 2 con Tauros cùng khóa mắt, cùng lùi 2 bước, cùng dậm móng lấy đà, và cùng phi nước đại lao vào nhau va sừng.
 * - Sử dụng Navigation chuẩn để di chuyển mượt mà, không bị trượt/teleport hay quay lưng.
 */
class HornClashGoal(private val entity: PokemonEntity) : Goal() {

    enum class ClashPhase {
        LOOK_LOCK,
        STEP_BACK,
        STOMP_WINDUP,
        CLASH_CHARGE,
        RECOIL_REST,
        FINISHED
    }

    class ClashSession(
        val entityA: PokemonEntity,
        val entityB: PokemonEntity,
        var phase: ClashPhase = ClashPhase.LOOK_LOCK,
        var stateTicks: Int = 0
    ) {
        fun getPartner(me: PokemonEntity): PokemonEntity = if (me == entityA) entityB else entityA
        fun isAlive(): Boolean = entityA.isAlive && entityB.isAlive && !entityA.isBattling && !entityB.isBattling
    }

    companion object {
        private val activeSessions = mutableMapOf<UUID, ClashSession>()
        private val cooldowns = mutableMapOf<UUID, Int>()

        fun getSession(entity: PokemonEntity): ClashSession? = activeSessions[entity.uuid]

        fun isBusy(entity: PokemonEntity): Boolean {
            return activeSessions.containsKey(entity.uuid) || (cooldowns[entity.uuid] ?: 0) > 0
        }

        fun tryStartSession(a: PokemonEntity, b: PokemonEntity): ClashSession? {
            if (isBusy(a) || isBusy(b)) return null
            val session = ClashSession(a, b)
            activeSessions[a.uuid] = session
            activeSessions[b.uuid] = session
            return session
        }

        fun endSession(session: ClashSession) {
            activeSessions.remove(session.entityA.uuid)
            activeSessions.remove(session.entityB.uuid)
            val cdA = session.entityA.random.nextInt(1200, 2400)
            val cdB = session.entityB.random.nextInt(1200, 2400)
            cooldowns[session.entityA.uuid] = cdA
            cooldowns[session.entityB.uuid] = cdB
        }
    }

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        if (entity.isBattling) return false
        val data = entity.getHerdData()
        if (!data.isInHerd || data.isStampeding) return false

        val existingSession = getSession(entity)
        if (existingSession != null && existingSession.isAlive()) return true

        val currentCd = cooldowns[entity.uuid] ?: 0
        if (currentCd > 0) {
            cooldowns[entity.uuid] = currentCd - 1
            return false
        }

        if (entity.random.nextInt(60) != 0) {
            return false
        }

        val clashRadius = 7.0 * entity.pokemon.scaleModifier.coerceIn(0.85f, 1.15f)
        val box = AABB.ofSize(entity.position(), clashRadius * 2.0, 6.0, clashRadius * 2.0)
        val candidate = entity.level().getEntitiesOfClass(PokemonEntity::class.java, box) {
            val maxDistance = clashRadius * ((entity.pokemon.scaleModifier + it.pokemon.scaleModifier) / 2.0)
                .coerceIn(0.85, 1.15)
            it != entity && it.isAlive && !it.isBattling &&
            it.getHerdData().herdId == data.herdId &&
            !it.getHerdData().isStampeding &&
            !isBusy(it) && entity.distanceToSqr(it) in 9.0..(maxDistance * maxDistance)
        }.firstOrNull() ?: return false

        return tryStartSession(entity, candidate) != null
    }

    override fun canContinueToUse(): Boolean {
        val session = getSession(entity) ?: return false
        return session.isAlive() && session.phase != ClashPhase.FINISHED
    }

    override fun start() {
        entity.isSprinting = false
    }

    override fun tick() {
        val session = getSession(entity) ?: return
        val partner = session.getPartner(entity)
        val level = entity.level() as? ServerLevel

        if (entity == session.entityA) {
            session.stateTicks++
        }

        lookAtPartner(partner)

        when (session.phase) {
            ClashPhase.LOOK_LOCK -> {
                entity.navigation.stop()
                entity.isSprinting = false

                if (session.stateTicks >= 30) {
                    if (entity == session.entityA) {
                        session.phase = ClashPhase.STEP_BACK
                        session.stateTicks = 0
                    }
                }
            }

            ClashPhase.STEP_BACK -> {
                entity.isSprinting = false
                val dir = entity.position().subtract(partner.position()).normalize()
                val backTargetX = entity.x + dir.x * 2.0
                val backTargetZ = entity.z + dir.z * 2.0
                entity.navigation.moveTo(backTargetX, entity.y, backTargetZ, 0.40)

                if (session.stateTicks >= 25 || entity.distanceToSqr(partner) >= 30.0) {
                    entity.navigation.stop()
                    if (entity == session.entityA) {
                        session.phase = ClashPhase.STOMP_WINDUP
                        session.stateTicks = 0
                    }
                }
            }

            ClashPhase.STOMP_WINDUP -> {
                entity.navigation.stop()
                entity.isSprinting = false

                if (session.stateTicks % 6 == 0 && level != null) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, entity.x, entity.y + 0.1, entity.z, 3, 0.2, 0.0, 0.2, 0.02)
                }

                if (session.stateTicks >= 30) {
                    if (entity == session.entityA) {
                        session.phase = ClashPhase.CLASH_CHARGE
                        session.stateTicks = 0
                    }
                }
            }

            ClashPhase.CLASH_CHARGE -> {
                entity.isSprinting = true

                val midX = (entity.x + partner.x) / 2.0
                val midZ = (entity.z + partner.z) / 2.0
                entity.navigation.moveTo(midX, entity.y, midZ, 0.70)

                if (entity.distanceToSqr(partner) < 4.0 || session.stateTicks >= 25) {
                    entity.isSprinting = false
                    entity.navigation.stop()

                    if (entity == session.entityA && level != null) {
                        val contactX = (entity.x + partner.x) / 2.0
                        val contactZ = (entity.z + partner.z) / 2.0
                        level.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0f, 1.2f)
                        level.playSound(null, entity.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.NEUTRAL, 0.9f, 0.9f)
                        level.sendParticles(ParticleTypes.CRIT, contactX, entity.y + 0.8, contactZ, 25, 0.25, 0.2, 0.25, 0.15)
                    }

                    val recoilDir = entity.position().subtract(partner.position()).normalize()
                    entity.knockback(0.4, -recoilDir.x, -recoilDir.z)

                    if (entity == session.entityA) {
                        session.phase = ClashPhase.RECOIL_REST
                        session.stateTicks = 0
                    }
                }
            }

            ClashPhase.RECOIL_REST -> {
                entity.isSprinting = false
                entity.navigation.stop()

                if (session.stateTicks >= 40) {
                    if (entity == session.entityA) {
                        session.phase = ClashPhase.FINISHED
                    }
                }
            }

            ClashPhase.FINISHED -> {
                stop()
            }
        }
    }

    private fun lookAtPartner(partner: PokemonEntity) {
        val dir = partner.position().subtract(entity.position())
        val targetYaw = (Math.toDegrees(Math.atan2(-dir.x, dir.z))).toFloat()
        entity.setYRot(targetYaw)
        entity.yBodyRot = targetYaw
        entity.yHeadRot = targetYaw
        entity.lookControl.setLookAt(partner.x, partner.eyeY, partner.z, 30.0f, 30.0f)
    }

    override fun stop() {
        val session = getSession(entity)
        if (session != null) {
            endSession(session)
        }
        entity.isSprinting = false
        entity.navigation.stop()
    }
}
