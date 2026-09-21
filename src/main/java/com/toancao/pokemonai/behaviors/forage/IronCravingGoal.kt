package com.toancao.pokemonai.behaviors.forage

import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.config.AronConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue

class IronCravingGoal(private val entity: PokemonEntity) : Goal() {
    private data class RouteNode(val pos: BlockPos, val cost: Double, val score: Double, val digs: Int)

    private val mob = entity as Mob
    private var targetPos: BlockPos? = null
    private var scanCooldown = AronConfigManager.config.scanInterval
    private var activeTicks = 0
    private var restTicks = 0
    private var digBudget = 0
    private var mode = MODE_SEEK
    private var progressTicks = 0
    private var lastStage = -1
    private var digTarget: BlockPos? = null
    private var fetchItem: ItemEntity? = null
    private var dropSearchPos: BlockPos? = null
    private var dropSearchTicks = 0
    private var ignoredDropIds: Set<Int> = emptySet()
    private var plannedPath = ArrayDeque<BlockPos>()

    private val cfg get() = AronConfigManager.config

    init {
        flags = java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        if (mob.level().isClientSide) return false
        if (restTicks > 0) {
            restTicks--
            return false
        }
        if (!com.toancao.pokemonai.utils.AIFilter.isEligible(entity)) return false
        if (com.toancao.pokemonai.compat.CobblemonBridge.getSpeciesName(entity) != SPECIES) return false
        if (--scanCooldown > 0) return false
        scanCooldown = cfg.scanInterval.coerceAtLeast(1)
        sweepTotal++
        val level = mob.level() as? ServerLevel ?: return false
        val found = findRayTarget(entity) ?: return false
        val path = planPath(level, found) ?: return false
        sweepHit++
        targetPos = found
        plannedPath = ArrayDeque(path)
        activeTicks = 0
        digBudget = cfg.maxDigBlocks
        mode = MODE_SEEK
        progressTicks = 0
        lastStage = -1
        digTarget = null
        fetchItem = null
        dropSearchPos = null
        dropSearchTicks = 0
        ignoredDropIds = emptySet()
        return true
    }

    override fun canContinueToUse(): Boolean {
        if (mob.level().isClientSide) return false
        if (targetPos == null && fetchItem == null && dropSearchPos == null) return false
        if (!com.toancao.pokemonai.utils.AIFilter.isEligible(entity)) return false
        return activeTicks < cfg.maxActiveTicks
    }

    override fun start() {
        activeTicks = 0
        startTotal++
        entity.addTag(TAG_ACTIVE)
        moveToNextStep()
    }

    override fun stop() {
        val level = mob.level() as? ServerLevel
        if (level != null) {
            val crackPos = when (mode) {
                MODE_GNAW -> targetPos
                MODE_DIG -> digTarget
                else -> null
            }
            if (crackPos != null) clearCrack(level, crackPos)
        }
        entity.removeTag(TAG_ACTIVE)
        targetPos = null
        digTarget = null
        fetchItem = null
        dropSearchPos = null
        ignoredDropIds = emptySet()
        plannedPath.clear()
        mode = MODE_SEEK
    }

    override fun tick() {
        val level = mob.level() as? ServerLevel ?: return
        activeTicks++
        when (mode) {
            MODE_GNAW -> tickGnaw(level)
            MODE_DIG -> tickDig(level)
            MODE_FETCH -> tickFetch(level)
            else -> tickSeek(level)
        }
    }

    private fun tickSeek(level: ServerLevel) {
        val target = targetPos ?: return
        if (!isIronFood(level, target)) {
            finishWithoutMeal(cfg.shortRest)
            return
        }

        while (plannedPath.isNotEmpty() && reachedWaypoint(plannedPath.first())) {
            plannedPath.removeFirst()
        }

        val next = plannedPath.firstOrNull()
        if (next == null) {
            if (distanceToBlockSqr(target) <= cfg.interactionDistance * cfg.interactionDistance) {
                beginGnaw(target)
                return
            }
            val replacement = planPath(level, target)
            if (replacement == null || replacement.isEmpty()) {
                finishWithoutMeal(cfg.longRest)
                return
            }
            plannedPath = ArrayDeque(replacement)
            moveToNextStep()
            return
        }

        if (isDiggable(level, next)) {
            if (digBudget <= 0) {
                finishWithoutMeal(cfg.longRest)
                return
            }
            if (distanceToBlockSqr(next) <= cfg.interactionDistance * cfg.interactionDistance) {
                beginDig(next)
            } else if (activeTicks % cfg.repathInterval.coerceAtLeast(1) == 0) {
                replanOrStop(level, target)
            }
            return
        }

        if (!isOpen(level, next)) {
            replanOrStop(level, target)
            return
        }

        if (activeTicks % cfg.repathInterval.coerceAtLeast(1) == 0 || !mob.navigation.isInProgress) {
            mob.navigation.moveTo(next.x + 0.5, next.y.toDouble(), next.z + 0.5, cfg.moveSpeed)
        }
    }

    private fun beginGnaw(target: BlockPos) {
        mode = MODE_GNAW
        progressTicks = 0
        lastStage = -1
        mob.navigation.stop()
        faceTarget(target.x + 0.5, target.y + 0.5, target.z + 0.5)
        com.toancao.pokemonai.compat.CobblemonBridge.playCry(entity)
    }

    private fun tickGnaw(level: ServerLevel) {
        val target = targetPos ?: return
        if (!isIronFood(level, target)) {
            finishWithoutMeal(cfg.shortRest)
            return
        }
        if (distanceToBlockSqr(target) > cfg.interactionDistance * cfg.interactionDistance) {
            mode = MODE_SEEK
            val replacement = planPath(level, target)
            if (replacement == null) finishWithoutMeal(cfg.longRest) else plannedPath = ArrayDeque(replacement)
            return
        }
        if (mob.navigation.isInProgress) mob.navigation.stop()
        faceTarget(target.x + 0.5, target.y + 0.5, target.z + 0.5)
        progressTicks++
        sendCrackStage(level, target, progressTicks, cfg.gnawTicks)
        if (progressTicks % cfg.gnawParticleInterval.coerceAtLeast(1) == 0) {
            spawnBiteParticles(level, target, cfg.gnawParticleCount)
        }
        if (progressTicks % cfg.gnawSoundInterval.coerceAtLeast(1) == 0) {
            level.playSound(null, target.x + 0.5, target.y + 0.5, target.z + 0.5, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0f, 0.9f)
        }
        if (progressTicks < cfg.gnawTicks) return
        val eatenState = level.getBlockState(target)
        ignoredDropIds = findDroppedItems(level, target).mapTo(HashSet()) { it.id }
        clearCrack(level, target)
        level.destroyBlock(target, true)
        level.playSound(null, target.x + 0.5, target.y + 0.5, target.z + 0.5, SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 0.7f, 1.1f)
        spawnBreakBurst(level, target, eatenState, cfg.gnawParticleCount * 2)
        breakTotal++
        dropSearchPos = target.immutable()
        dropSearchTicks = 0
        fetchItem = findNewDroppedItem(level, target)
        targetPos = null
        plannedPath.clear()
        mode = MODE_FETCH
        progressTicks = 0
        val item = fetchItem
        if (item != null) mob.navigation.moveTo(item.x, item.y, item.z, cfg.moveSpeed)
    }

    private fun beginDig(step: BlockPos) {
        digTarget = step
        mode = MODE_DIG
        progressTicks = 0
        lastStage = -1
        mob.navigation.stop()
        faceTarget(step.x + 0.5, step.y + 0.5, step.z + 0.5)
    }

    private fun tickDig(level: ServerLevel) {
        val step = digTarget ?: run {
            mode = MODE_SEEK
            return
        }
        if (!isDiggable(level, step)) {
            clearCrack(level, step)
            digTarget = null
            mode = MODE_SEEK
            return
        }
        if (distanceToBlockSqr(step) > cfg.interactionDistance * cfg.interactionDistance) {
            clearCrack(level, step)
            digTarget = null
            mode = MODE_SEEK
            return
        }
        if (mob.navigation.isInProgress) mob.navigation.stop()
        faceTarget(step.x + 0.5, step.y + 0.5, step.z + 0.5)
        progressTicks++
        sendCrackStage(level, step, progressTicks, cfg.digTicks)
        if (progressTicks % cfg.digParticleInterval.coerceAtLeast(1) == 0) {
            spawnBiteParticles(level, step, cfg.digParticleCount)
        }
        if (progressTicks % cfg.digSoundInterval.coerceAtLeast(1) == 0) {
            level.playSound(null, step.x + 0.5, step.y + 0.5, step.z + 0.5, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 0.6f)
        }
        if (progressTicks < cfg.digTicks) return
        val dugState = level.getBlockState(step)
        clearCrack(level, step)
        level.destroyBlock(step, false)
        spawnBreakBurst(level, step, dugState, cfg.digParticleCount * 2)
        digTotal++
        digBudget--
        digTarget = null
        mode = MODE_SEEK
        moveToNextStep()
    }

    private fun tickFetch(level: ServerLevel) {
        var item = fetchItem
        if (item == null || item.isRemoved || item.item.isEmpty) {
            val searchPos = dropSearchPos
            if (searchPos == null || dropSearchTicks >= cfg.dropSearchTicks) {
                fetchItem = null
                dropSearchPos = null
                ignoredDropIds = emptySet()
                restTicks = cfg.shortRest
                return
            }
            dropSearchTicks++
            item = findNewDroppedItem(level, searchPos)
            fetchItem = item
            if (item == null) {
                mob.navigation.stop()
                return
            }
        }
        val pickupDistSqr = cfg.itemPickupDistance * cfg.itemPickupDistance
        if (mob.distanceToSqr(item) > pickupDistSqr) {
            progressTicks = 0
            if (activeTicks % cfg.repathInterval.coerceAtLeast(1) == 0 || !mob.navigation.isInProgress) {
                mob.navigation.moveTo(item.x, item.y, item.z, cfg.moveSpeed)
            }
            return
        }
        if (mob.navigation.isInProgress) mob.navigation.stop()
        faceTarget(item.x, item.y + 0.25, item.z)
        progressTicks++
        if (progressTicks % cfg.eatSoundInterval.coerceAtLeast(1) == 0) {
            level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.9f, 0.8f)
        }
        if (progressTicks >= cfg.eatNibbleTicks) eatItem(level, item)
    }

    private fun eatItem(level: ServerLevel, item: ItemEntity) {
        item.discard()
        com.toancao.pokemonai.compat.CobblemonBridge.playCry(entity)
        level.playSound(null, mob.x, mob.y, mob.z, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0f, 1.0f)
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.x, mob.y + 1.0, mob.z, 8, 0.4, 0.4, 0.4, 0.05)
        try {
            val pokemon = com.toancao.pokemonai.compat.CobblemonBridge.getPokemonData(entity)
            val xp = (pokemon.getExperienceToNextLevel() / cfg.xpFraction.coerceAtLeast(1)).coerceAtLeast(1)
            pokemon.addExperience(SidemodExperienceSource(SOURCE_ID), xp)
        } catch (_: Exception) {
        }
        eatTotal++
        fetchItem = null
        dropSearchPos = null
        ignoredDropIds = emptySet()
        restTicks = cfg.mealRest
    }

    private fun planPath(level: ServerLevel, target: BlockPos): List<BlockPos>? {
        val start = mob.blockPosition().immutable()
        val padding = cfg.pathSearchPadding.coerceAtLeast(1)
        val minX = minOf(start.x, target.x) - padding
        val maxX = maxOf(start.x, target.x) + padding
        val minY = maxOf(level.minBuildHeight + 1, minOf(start.y, target.y) - padding)
        val maxY = minOf(level.maxBuildHeight - 1, maxOf(start.y, target.y) + padding)
        val minZ = minOf(start.z, target.z) - padding
        val maxZ = maxOf(start.z, target.z) + padding
        val goals = Direction.entries
            .map { target.relative(it).immutable() }
            .filter { it.y in minY..maxY && canOccupy(level, it, start, target) }
            .toSet()
        if (start in goals) return emptyList()
        if (goals.isEmpty()) return null

        val queue = PriorityQueue<RouteNode>(compareBy<RouteNode> { it.score }.thenBy { it.cost })
        val costs = HashMap<BlockPos, Double>()
        val parents = HashMap<BlockPos, BlockPos>()
        costs[start] = 0.0
        queue.add(RouteNode(start, 0.0, heuristic(start, target), 0))
        var visited = 0

        while (queue.isNotEmpty() && visited < cfg.pathSearchMaxNodes.coerceAtLeast(1)) {
            val current = queue.poll()
            if (current.cost > (costs[current.pos] ?: Double.MAX_VALUE)) continue
            visited++
            if (current.pos in goals) return reconstructPath(current.pos, start, parents)

            for (direction in Direction.entries) {
                val next = current.pos.relative(direction).immutable()
                if (next.x !in minX..maxX || next.y !in minY..maxY || next.z !in minZ..maxZ) continue
                if (!canOccupy(level, next, start, target)) continue
                val needsDig = isDiggable(level, next)
                val digs = current.digs + if (needsDig) 1 else 0
                if (digs > cfg.maxDigBlocks) continue
                val stepCost = if (needsDig) cfg.pathBreakCost.coerceAtLeast(1.0) else 1.0
                val nextCost = current.cost + stepCost
                if (nextCost >= (costs[next] ?: Double.MAX_VALUE)) continue
                costs[next] = nextCost
                parents[next] = current.pos
                queue.add(RouteNode(next, nextCost, nextCost + heuristic(next, target), digs))
            }
        }
        return null
    }

    private fun canOccupy(level: ServerLevel, pos: BlockPos, start: BlockPos, target: BlockPos): Boolean {
        if (pos == target) return false
        if (pos == start) return true
        if (isDiggable(level, pos)) return true
        if (!isOpen(level, pos)) return false
        val below = pos.below()
        return !isOpen(level, below) || isDiggable(level, below)
    }

    private fun reconstructPath(end: BlockPos, start: BlockPos, parents: Map<BlockPos, BlockPos>): List<BlockPos> {
        val reversed = ArrayList<BlockPos>()
        var cursor = end
        while (cursor != start) {
            reversed.add(cursor)
            cursor = parents[cursor] ?: return emptyList()
        }
        reversed.reverse()
        return reversed
    }

    private fun heuristic(pos: BlockPos, target: BlockPos): Double {
        return (kotlin.math.abs(pos.x - target.x) + kotlin.math.abs(pos.y - target.y) + kotlin.math.abs(pos.z - target.z) - 1)
            .coerceAtLeast(0).toDouble()
    }

    private fun replanOrStop(level: ServerLevel, target: BlockPos) {
        val replacement = planPath(level, target)
        if (replacement == null) {
            finishWithoutMeal(cfg.longRest)
        } else {
            plannedPath = ArrayDeque(replacement)
            moveToNextStep()
        }
    }

    private fun moveToNextStep() {
        val next = plannedPath.firstOrNull() ?: return
        mob.navigation.moveTo(next.x + 0.5, next.y.toDouble(), next.z + 0.5, cfg.moveSpeed)
    }

    private fun reachedWaypoint(pos: BlockPos): Boolean {
        if (mob.blockPosition() == pos) return true
        val dx = mob.x - (pos.x + 0.5)
        val dz = mob.z - (pos.z + 0.5)
        return dx * dx + dz * dz <= cfg.waypointDistance * cfg.waypointDistance && kotlin.math.abs(mob.y - pos.y) <= 1.0
    }

    private fun finishWithoutMeal(rest: Int) {
        mob.navigation.stop()
        targetPos = null
        digTarget = null
        plannedPath.clear()
        restTicks = rest
    }

    private fun distanceToBlockSqr(pos: BlockPos): Double {
        val mouthY = mob.y + mob.bbHeight * 0.55
        val px = Mth.clamp(mob.x, pos.x.toDouble(), pos.x + 1.0)
        val py = Mth.clamp(mouthY, pos.y.toDouble(), pos.y + 1.0)
        val pz = Mth.clamp(mob.z, pos.z.toDouble(), pos.z + 1.0)
        val dx = mob.x - px
        val dy = mouthY - py
        val dz = mob.z - pz
        return dx * dx + dy * dy + dz * dz
    }

    private fun faceTarget(x: Double, y: Double, z: Double) {
        val dx = x - mob.x
        val dz = z - mob.z
        if (dx * dx + dz * dz > 1.0E-6) {
            val yaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
            mob.setYRot(yaw)
            mob.yBodyRot = yaw
            mob.yHeadRot = yaw
        }
        mob.lookControl.setLookAt(x, y, z, 90.0f, 90.0f)
    }

    private fun spawnBiteParticles(level: ServerLevel, pos: BlockPos, count: Int) {
        val state = level.getBlockState(pos)
        repeat(count.coerceAtLeast(0)) {
            val point = randomPointOnFacingSurface(pos)
            level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, state), point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.03)
        }
        repeat((count / 2 + 1).coerceAtLeast(0)) {
            val point = randomPointOnFacingSurface(pos)
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0, 0.02, 0.0, 0.02)
        }
    }

    private fun randomPointOnFacingSurface(pos: BlockPos): Vec3 {
        val cx = pos.x + 0.5
        val cy = pos.y + 0.5
        val cz = pos.z + 0.5
        val dx = mob.x - cx
        val dy = mob.y + mob.bbHeight * 0.55 - cy
        val dz = mob.z - cz
        val a = 0.08 + mob.random.nextDouble() * 0.84
        val b = 0.08 + mob.random.nextDouble() * 0.84
        return if (kotlin.math.abs(dy) >= kotlin.math.abs(dx) && kotlin.math.abs(dy) >= kotlin.math.abs(dz)) {
            Vec3(pos.x + a, if (dy >= 0.0) pos.y + 1.01 else pos.y - 0.01, pos.z + b)
        } else if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) {
            Vec3(if (dx >= 0.0) pos.x + 1.01 else pos.x - 0.01, pos.y + a, pos.z + b)
        } else {
            Vec3(pos.x + a, pos.y + b, if (dz >= 0.0) pos.z + 1.01 else pos.z - 0.01)
        }
    }

    private fun spawnBreakBurst(level: ServerLevel, pos: BlockPos, state: BlockState, count: Int) {
        val cx = pos.x + 0.5
        val cy = pos.y + 0.5
        val cz = pos.z + 0.5
        level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, state), cx, cy, cz, count, 0.35, 0.35, 0.35, 0.08)
        level.sendParticles(ParticleTypes.CRIT, cx, cy, cz, count / 2 + 1, 0.3, 0.3, 0.3, 0.12)
    }

    private fun findDroppedItems(level: ServerLevel, pos: BlockPos): List<ItemEntity> {
        val center = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        return level.getEntitiesOfClass(
            ItemEntity::class.java,
            AABB.ofSize(center, cfg.fetchScanXZ, cfg.fetchScanY, cfg.fetchScanXZ)
        ) { !it.isRemoved && !it.item.isEmpty }
    }

    private fun findNewDroppedItem(level: ServerLevel, pos: BlockPos): ItemEntity? {
        val center = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        return findDroppedItems(level, pos)
            .asSequence()
            .filter { it.id !in ignoredDropIds }
            .minByOrNull { it.distanceToSqr(center) }
    }

    private fun sendCrackStage(level: ServerLevel, pos: BlockPos, ticks: Int, total: Int) {
        val stage = ((ticks * 10) / total.coerceAtLeast(1)).coerceIn(0, 9)
        if (stage == lastStage) return
        lastStage = stage
        sendCrack(level, pos, stage)
    }

    private fun clearCrack(level: ServerLevel, pos: BlockPos) {
        lastStage = -1
        sendCrack(level, pos, -1)
    }

    private fun sendCrack(level: ServerLevel, pos: BlockPos, stage: Int) {
        val packet = ClientboundBlockDestructionPacket(mob.id, pos, stage)
        val viewDistSqr = cfg.crackViewDistance * cfg.crackViewDistance
        for (player in level.players()) {
            if (player.distanceToSqr(mob.x, mob.y, mob.z) < viewDistSqr) player.connection.send(packet)
        }
    }

    private fun isIronFood(level: Level, pos: BlockPos): Boolean {
        return ironFoods().contains(level.getBlockState(pos).block)
    }

    private fun isOpen(level: Level, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.fluidState.isEmpty && state.getCollisionShape(level, pos).isEmpty
    }

    private fun isDiggable(level: ServerLevel, pos: BlockPos): Boolean {
        if (pos.y <= level.minBuildHeight) return false
        val state = level.getBlockState(pos)
        if (state.isAir || !state.fluidState.isEmpty) return false
        if (ironFoods().contains(state.block)) return false
        return diggables().contains(state.block)
    }

    companion object {
        const val SPECIES = "aron"
        const val SOURCE_ID = "tc_reborn"
        const val TAG_ACTIVE = "tc_iron_craving"
        const val MODE_SEEK = 0
        const val MODE_GNAW = 1
        const val MODE_DIG = 2
        const val MODE_FETCH = 3
        var sweepTotal = 0
        var sweepHit = 0
        var startTotal = 0
        var eatTotal = 0
        var digTotal = 0
        var breakTotal = 0

        private var cachedFoodKey: List<String> = emptyList()
        private var cachedFoods: Set<Block> = emptySet()
        private var cachedDigKey: List<String> = emptyList()
        private var cachedDigs: Set<Block> = emptySet()

        fun findRayTarget(entity: PokemonEntity): BlockPos? {
            val level = entity.level()
            val center = entity.blockPosition()
            val cfg = AronConfigManager.config
            var best: BlockPos? = null
            var bestDistance = Double.MAX_VALUE

            fun inspect(pos: BlockPos) {
                if (pos.y < level.minBuildHeight || pos.y > level.maxBuildHeight) return
                if (!ironFoods().contains(level.getBlockState(pos).block)) return
                val distance = center.distSqr(pos)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = pos.immutable()
                }
            }

            for (distance in 1..cfg.horizontalRayRange.coerceAtLeast(0)) {
                inspect(center.offset(distance, 0, 0))
                inspect(center.offset(-distance, 0, 0))
                inspect(center.offset(0, 0, distance))
                inspect(center.offset(0, 0, -distance))
            }
            for (distance in 1..cfg.downwardRayRange.coerceAtLeast(0)) {
                inspect(center.below(distance))
            }

            val look = entity.lookAngle
            val horizontalLength = kotlin.math.sqrt(look.x * look.x + look.z * look.z)
            val lookX = if (horizontalLength > 1.0E-6) look.x / horizontalLength else -kotlin.math.sin(Math.toRadians(entity.yRot.toDouble()))
            val lookZ = if (horizontalLength > 1.0E-6) look.z / horizontalLength else kotlin.math.cos(Math.toRadians(entity.yRot.toDouble()))
            for (distance in 1..cfg.diagonalRayRange.coerceAtLeast(0)) {
                inspect(BlockPos.containing(entity.x + lookX * distance, center.y - distance.toDouble(), entity.z + lookZ * distance))
            }
            return best
        }

        fun ironFoods(): Set<Block> {
            val ids = AronConfigManager.config.ironFoodBlocks
            if (cachedFoodKey != ids) {
                cachedFoods = resolveBlocks(ids)
                cachedFoodKey = ids.toList()
            }
            return cachedFoods
        }

        fun diggables(): Set<Block> {
            val ids = AronConfigManager.config.diggableBlocks
            if (cachedDigKey != ids) {
                cachedDigs = resolveBlocks(ids)
                cachedDigKey = ids.toList()
            }
            return cachedDigs
        }

        fun resolveBlocks(ids: List<String>): Set<Block> {
            val out = LinkedHashSet<Block>()
            for (id in ids) {
                try {
                    val block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id))
                    if (block != Blocks.AIR || id == "minecraft:air") out.add(block)
                } catch (_: Exception) {
                }
            }
            return out
        }
    }
}
