package com.toancao.pokemonai.behaviors.herd

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.behaviors.combat.BlockBreakHandler
import com.toancao.pokemonai.behaviors.combat.HerdSharedAggroGoal
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.spawner.hierarchy.setHerdData
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

/**
 * Cơ chế đại xung phong phẫn nộ:
 * - Con đầu đàn chạy lên trước tiên phong.
 * - Các con sau quan sát và nối đuôi chạy theo hướng của con đầu đàn.
 * - Hỗ trợ vượt địa hình mấp mô, dao động Y, bước qua các bậc đá 1.25 block.
 * - Phá vỡ 4 - 5 block chướng ngại vật trước khi bị kiệt sức/dừng hoảng loạn.
 * - Khi con đầu đàn dừng phẫn nộ -> Cả đàn lập tức dừng theo và BẬT LẠI tính năng bầy đàn.
 */
class HerdStampedeGoal(
    private val entity: PokemonEntity,
    private val marchSpeed: Double = 0.55
) : Goal() {

    private var targetYaw: Float = 0f
    private var recalculateTimer = 0
    private var brokenBlockCount = 0
    private val maxBrokenBlocksToStop = 5

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        if (entity.isBattling) return false
        val data = entity.getHerdData()
        return data.isStampeding && data.stampedeTicksRemaining > 0
    }

    override fun canContinueToUse(): Boolean {
        if (entity.isBattling) return false
        val data = entity.getHerdData()
        return data.isStampeding && data.stampedeTicksRemaining > 0
    }

    override fun start() {
        val data = entity.getHerdData()
        val dirX = data.stampedeDirX
        val dirZ = data.stampedeDirZ
        brokenBlockCount = 0

        targetYaw = (Math.toDegrees(Math.atan2(-dirX, dirZ))).toFloat()
        entity.setYRot(targetYaw)
        entity.yBodyRot = targetYaw
        entity.yHeadRot = targetYaw

        entity.attributes.getInstance(Attributes.STEP_HEIGHT)?.baseValue = 1.25
        entity.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.OPEN, 0.0f)
        entity.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DANGER_OTHER, 0.0f)

        recalculateTimer = 0
        if (data.stampedeDelayTicks <= 0) {
            updatePathfinderTarget()
        }
    }

    private fun updatePathfinderTarget() {
        val level = entity.level() as? ServerLevel ?: return
        val data = entity.getHerdData()
        val dirX = data.stampedeDirX
        val dirZ = data.stampedeDirZ

        val targetX = entity.x + dirX * 16.0
        val targetZ = entity.z + dirZ * 16.0
        val blockX = targetX.toInt()
        val blockZ = targetZ.toInt()
        val groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ).toDouble()
        val targetPos = BlockPos(blockX, groundY.toInt(), blockZ)

        entity.brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET, net.minecraft.world.entity.ai.memory.WalkTarget(targetPos, marchSpeed.toFloat(), 1))
        entity.brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET, net.minecraft.world.entity.ai.behavior.BlockPosTracker(targetPos))

        entity.navigation.moveTo(targetX, groundY, targetZ, marchSpeed)
    }

    override fun tick() {
        val data = entity.getHerdData()
        data.stampedeTicksRemaining--

        val level = entity.level() as? ServerLevel ?: return
        val dirX = data.stampedeDirX
        val dirZ = data.stampedeDirZ

        entity.setYRot(targetYaw)
        entity.yBodyRot = targetYaw
        entity.yHeadRot = targetYaw

        val lookAhead = Vec3(entity.x + dirX * 10.0, entity.y, entity.z + dirZ * 10.0)
        entity.lookControl.setLookAt(lookAhead.x, lookAhead.y, lookAhead.z, 30.0f, 30.0f)

        if (data.stampedeDelayTicks > 0) {
            data.stampedeDelayTicks--
            entity.setHerdData(data)
            entity.isSprinting = false
            if (entity.tickCount % 6 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, entity.x, entity.y + 0.1, entity.z, 1, 0.1, 0.05, 0.1, 0.01)
            }
            return
        }
        entity.setHerdData(data)

        entity.isSprinting = true

        if (entity.horizontalCollision && entity.onGround()) {
            entity.deltaMovement = Vec3(entity.deltaMovement.x, 0.38, entity.deltaMovement.z)
        }

        val frontGroundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (entity.x + dirX * 1.2).toInt(), (entity.z + dirZ * 1.2).toInt())
        if (frontGroundY < entity.y - 0.4 && entity.onGround()) {
            entity.deltaMovement = Vec3(dirX * marchSpeed * 0.7, entity.deltaMovement.y, dirZ * marchSpeed * 0.7)
        }

        if (entity.navigation.isDone || --recalculateTimer <= 0) {
            recalculateTimer = 20
            updatePathfinderTarget()
        }

        if (entity.tickCount % 4 == 0) {
            level.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                entity.x, entity.y + 0.1, entity.z,
                1, 0.2, 0.05, 0.2, 0.01
            )
        }

        if (entity.tickCount % 16 == 0) {
            level.playSound(null, entity.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.NEUTRAL, 0.7f, 0.95f)
        }

        val frontCenter = BlockPos.containing(entity.x + dirX * 1.2, entity.y + 0.5, entity.z + dirZ * 1.2)
        val newlyBroken = BlockBreakHandler.tryBreakBlocksInBox(level, frontCenter.offset(-1, 0, -1), frontCenter.offset(1, 1, 1))
        if (newlyBroken > 0) {
            brokenBlockCount += newlyBroken
            level.playSound(null, entity.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1.2f, 1.0f)
            level.sendParticles(ParticleTypes.CRIT, entity.x + dirX, entity.y + 0.5, entity.z + dirZ, 6, 0.2, 0.2, 0.2, 0.1)

            if (brokenBlockCount >= maxBrokenBlocksToStop) {
                level.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.9f, 1.2f)
                level.sendParticles(ParticleTypes.EXPLOSION, entity.x + dirX, entity.y + 0.5, entity.z + dirZ, 1, 0.0, 0.0, 0.0, 0.0)
                stop()
                return
            }
        }

        val collisionBox = entity.boundingBox.inflate(0.5)
        val victims = level.getEntitiesOfClass(LivingEntity::class.java, collisionBox) {
            it != entity && !it.tags.contains("tc_herd_entity") && it.isAlive
        }
        for (victim in victims) {
            victim.hurt(entity.damageSources().mobAttack(entity), 4.0f)
            victim.knockback(0.8, -dirX, -dirZ)
        }

        if (data.stampedeTicksRemaining <= 0) {
            stop()
        }
    }

    override fun stop() {
        val data = entity.getHerdData()
        data.isStampeding = false
        data.stampedeDelayTicks = 0
        data.stampedeTicksRemaining = 0
        entity.setHerdData(data)
        entity.isSprinting = false
        brokenBlockCount = 0

        entity.attributes.getInstance(Attributes.STEP_HEIGHT)?.baseValue = 0.6

        entity.brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET)
        entity.navigation.stop()

        val level = entity.level() as? ServerLevel ?: return
        level.sendParticles(ParticleTypes.SMOKE, entity.x, entity.y + 1.0, entity.z, 4, 0.2, 0.2, 0.2, 0.01)

        if (data.isLeader && data.herdId != null) {
            val searchBox = AABB.ofSize(entity.position(), 128.0, 64.0, 128.0)
            HerdSharedAggroGoal.stopHerdStampede(level, data.herdId!!, searchBox)
        }
    }
}
