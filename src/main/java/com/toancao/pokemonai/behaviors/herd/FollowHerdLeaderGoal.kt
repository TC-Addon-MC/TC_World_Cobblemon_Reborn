package com.toancao.pokemonai.behaviors.herd

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import java.util.EnumSet

/**
 * Hành vi đàn em bám theo con đầu đàn:
 * - Chuẩn hóa giao tiếp trực tiếp qua Brain MemoryModuleType.WALK_TARGET của Cobblemon gốc.
 * - Tự động đồng bộ với hoạt ảnh MoLang và OmniPathNavigation.
 */
class FollowHerdLeaderGoal(private val entity: PokemonEntity) : Goal() {

    private var leader: PokemonEntity? = null
    private var timeToRecalcPath = 0

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        val data = entity.getHerdData()
        if (!data.isInHerd || data.isLeader) return false
        if (entity.isBattling) return false
        if (entity.target != null && entity.target!!.isAlive) return false
        if (data.isStampeding) return false

        val leaderUUID = data.leaderUUID ?: return false
        val box = AABB.ofSize(entity.position(), 128.0, 64.0, 128.0)
        val found = entity.level().getEntitiesOfClass(PokemonEntity::class.java, box) {
            it.uuid == leaderUUID && it.isAlive
        }.firstOrNull() ?: return false

        leader = found
        val distSq = entity.distanceToSqr(found)

        val desiredDist = getDesiredDistanceBehind(data.formationIndex, found)
        val triggerDist = desiredDist + 6.0
        return distSq > (triggerDist * triggerDist)
    }

    override fun canContinueToUse(): Boolean {
        if (entity.isBattling) return false
        if (entity.target != null && entity.target!!.isAlive) return false
        val data = entity.getHerdData()
        if (data.isStampeding) return false

        val currentLeader = leader ?: return false
        if (!currentLeader.isAlive) return false

        val distSq = entity.distanceToSqr(currentLeader)
        val desiredDist = getDesiredDistanceBehind(data.formationIndex, currentLeader)

        return distSq > (desiredDist * desiredDist) && distSq < 3600.0
    }

    private fun getDesiredDistanceBehind(formationIndex: Int, currentLeader: PokemonEntity): Double {
        val row = ((formationIndex.coerceAtLeast(1) + 1) / 2)
        return (3.0 + row * 2.5) * sizeSpacingModifier(currentLeader)
    }

    override fun start() {
        timeToRecalcPath = 0
        entity.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.OPEN, 0.0f)
        entity.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DANGER_OTHER, 0.0f)
        entity.attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT)?.baseValue = 1.0
    }

    override fun tick() {
        val currentLeader = leader ?: return
        entity.lookControl.setLookAt(currentLeader, 10.0f, entity.maxHeadXRot.toFloat())

        val data = entity.getHerdData()
        val fIndex = data.formationIndex.coerceAtLeast(1)

        val leaderYawRad = Math.toRadians(currentLeader.yRot.toDouble())
        val forwardX = -Math.sin(leaderYawRad)
        val forwardZ = Math.cos(leaderYawRad)
        val rightX = Math.cos(leaderYawRad)
        val rightZ = Math.sin(leaderYawRad)

        val row = (fIndex + 1) / 2
        val side = if (fIndex % 2 == 1) 1.0 else -1.0

        val spacing = sizeSpacingModifier(currentLeader)
        val behindDist = (3.5 + row * 2.5) * spacing
        val lateralDist = side * (row * 2.0) * spacing

        val targetX = currentLeader.x - (forwardX * behindDist) + (rightX * lateralDist)
        val targetZ = currentLeader.z - (forwardZ * behindDist) + (rightZ * lateralDist)

        val distSq = entity.distanceToSqr(currentLeader)
        val speedModifier = if (distSq > 400.0) 0.70 else 0.55

        if (entity.horizontalCollision && entity.onGround()) {
            entity.deltaMovement = net.minecraft.world.phys.Vec3(entity.deltaMovement.x, 0.35, entity.deltaMovement.z)
        }

        val level = entity.level()
        val targetDir = net.minecraft.world.phys.Vec3(targetX - entity.x, 0.0, targetZ - entity.z).normalize()
        if (targetDir.lengthSqr() > 0.01) {
            val frontGroundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (entity.x + targetDir.x * 1.0).toInt(), (entity.z + targetDir.z * 1.0).toInt())
            if (frontGroundY < entity.y - 0.4 && entity.onGround()) {
                entity.deltaMovement = net.minecraft.world.phys.Vec3(targetDir.x * speedModifier * 0.55, entity.deltaMovement.y, targetDir.z * speedModifier * 0.55)
            }
        }

        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = 15

            val blockX = targetX.toInt()
            val blockZ = targetZ.toInt()
            val groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ).toDouble()
            val targetPos = BlockPos(blockX, groundY.toInt(), blockZ)

            entity.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(targetPos, speedModifier.toFloat(), 1))
            entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(targetPos))

            entity.navigation.moveTo(targetX, groundY, targetZ, speedModifier)
        }
    }

    override fun stop() {
        leader = null
        entity.attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT)?.baseValue = 0.6
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.LOOK_TARGET)
        entity.navigation.stop()
    }

    private fun sizeSpacingModifier(currentLeader: PokemonEntity): Double {
        val averageScale = (entity.pokemon.scaleModifier + currentLeader.pokemon.scaleModifier) / 2.0
        return averageScale.coerceIn(0.85, 1.15)
    }
}
