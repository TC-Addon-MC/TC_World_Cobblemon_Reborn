@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.blocks.entity

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.blocks.DragonGateTopBlock
import com.toancao.pokemonai.events.DragonGateEvent
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.particles.ParticleTypes

class DragonGateBottomBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_BOTTOM_BLOCK_ENTITY, pos, state) {
    var topPos: BlockPos? = null
    var cachedPath: List<BlockPos>? = null
    var pathUpdateTicks = 0

    init {
        com.toancao.pokemonai.events.DragonGateEvent.bottomBlocks.add(pos)
    }

    fun buildWaypointChain(level: ServerLevel, ultimateTopPos: BlockPos) {
        val path = findWaterPath(level, worldPosition, ultimateTopPos)
        if (path == null || path.size < 8) {
            this.topPos = ultimateTopPos
            setChanged()
            return
        }

        // Phá các vật cản dọc theo ĐƯỜNG ĐI ĐÃ TÍNH TOÁN
        explodeTunnel(level, path)

        val waypointsToPlace = mutableListOf<BlockPos>()
        var i = 8
        while (i < path.size - 4) {
            waypointsToPlace.add(path[i])
            i += 8
        }

        var currentTarget = ultimateTopPos
        
        for (wpPos in waypointsToPlace.reversed()) {
            val isWater = level.getFluidState(wpPos).`is`(net.minecraft.tags.FluidTags.WATER)
            
            // Place Waypoint block
            level.setBlock(wpPos, com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_WAYPOINT_BLOCK.defaultBlockState().setValue(com.toancao.pokemonai.blocks.DragonGateWaypointBlock.WATERLOGGED, isWater), 3)
            
            val wpBe = level.getBlockEntity(wpPos) as? com.toancao.pokemonai.blocks.entity.DragonGateWaypointBlockEntity
            if (wpBe != null) {
                wpBe.updateNextPos(currentTarget)
            }
            currentTarget = wpPos
        }

        this.topPos = currentTarget
        setChanged()
    }

    private fun explodeTunnel(level: ServerLevel, path: List<BlockPos>) {
        for (centerPos in path) {
            // Rộng 3x3 (-1 đến 1), cao 1 đến 3 block phía trên mặt nước
            for (ox in -1..1) {
                for (oy in 1..3) {
                    for (oz in -1..1) {
                        val checkPos = centerPos.offset(ox, oy, oz)
                        val state = level.getBlockState(checkPos)
                        
                        val isLeaves = state.`is`(net.minecraft.tags.BlockTags.LEAVES)
                        val isLogs = state.`is`(net.minecraft.tags.BlockTags.LOGS)
                        
                        // Kiểm tra xem block có thuộc mod TC không (để giữ lại)
                        val regName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block)
                        val isTcBlock = regName.namespace == "tc" || regName.namespace == "pokemonai"
                        
                        // Chỉ phá hủy khi nó là lá cây hoặc khối gỗ, và không thuộc mod TC
                        if (!isTcBlock && (isLeaves || isLogs)) {
                            level.destroyBlock(checkPos, true)
                        }
                    }
                }
            }
        }
    }

    // A* Pathfinding method through water
    fun findWaterPath(level: ServerLevel, start: BlockPos, end: BlockPos): List<BlockPos>? {
        val maxNodes = 1000
        val openSet = java.util.PriorityQueue<PathNode>(compareBy { it.f })
        val allNodes = mutableMapOf<BlockPos, PathNode>()
        
        val startNode = PathNode(start, 0.0, start.distSqr(end).toDouble(), null)
        openSet.add(startNode)
        allNodes[start] = startNode
        
        var nodesEvaluated = 0
        val directions = arrayOf(
            BlockPos(1, 0, 0), BlockPos(-1, 0, 0),
            BlockPos(0, 1, 0), BlockPos(0, -1, 0),
            BlockPos(0, 0, 1), BlockPos(0, 0, -1)
        )
        
        while (openSet.isNotEmpty() && nodesEvaluated < maxNodes) {
            val current = openSet.poll()
            if (current.pos == end || current.pos.distSqr(end) <= 2.0) {
                val path = mutableListOf<BlockPos>()
                var curr: PathNode? = current
                while (curr != null) {
                    path.add(0, curr.pos)
                    curr = curr.parent
                }
                return path
            }
            
            nodesEvaluated++
            for (dir in directions) {
                val neighborPos = current.pos.offset(dir)
                val isEnd = neighborPos == end || neighborPos.distSqr(end) <= 2.0
                val isWater = level.getFluidState(neighborPos).`is`(net.minecraft.tags.FluidTags.WATER) || 
                              level.getBlockState(neighborPos).block is com.toancao.pokemonai.blocks.DragonGateWaypointBlock ||
                              level.getBlockState(neighborPos).block is com.toancao.pokemonai.blocks.DragonGateBottomBlock ||
                              level.getBlockState(neighborPos).block is com.toancao.pokemonai.blocks.DragonGateTopBlock
                              
                if (!isEnd && !isWater) continue
                
                val tentativeG = current.g + 1.0
                val existingNode = allNodes[neighborPos]
                
                if (existingNode == null || tentativeG < existingNode.g) {
                    val h = neighborPos.distSqr(end).toDouble()
                    val neighborNode = PathNode(neighborPos, tentativeG, h, current)
                    allNodes[neighborPos] = neighborNode
                    if (existingNode == null) {
                        openSet.add(neighborNode)
                    } else {
                        openSet.remove(existingNode)
                        openSet.add(neighborNode)
                    }
                }
            }
        }
        return null
    }
    
    class PathNode(val pos: BlockPos, val g: Double, val h: Double, val parent: PathNode?) {
        val f: Double get() = g + h
    }

    override fun setRemoved() {
        super.setRemoved()
        com.toancao.pokemonai.events.DragonGateEvent.bottomBlocks.remove(worldPosition)
    }

    fun updateTopPos(pos: BlockPos) {
        this.topPos = pos
        setChanged()
    }

    override fun saveAdditional(tag: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        if (topPos != null) {
            tag.putInt("TopX", topPos!!.x)
            tag.putInt("TopY", topPos!!.y)
            tag.putInt("TopZ", topPos!!.z)
        }
    }

    override fun loadAdditional(tag: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        if (tag.contains("TopX")) {
            topPos = BlockPos(tag.getInt("TopX"), tag.getInt("TopY"), tag.getInt("TopZ"))
        }
        com.toancao.pokemonai.events.DragonGateEvent.bottomBlocks.add(worldPosition)
    }

    companion object {
        fun tick(level: Level, pos: BlockPos, state: BlockState, entity: DragonGateBottomBlockEntity) {
            if (level !is ServerLevel) return

            // Render particles for players holding the block
            val player = level.getNearestPlayer(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), 20.0, false)
            if (player != null) {
                val mainItem = player.mainHandItem.item
                val offItem = player.offhandItem.item
                if (mainItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_BOTTOM_BLOCK.asItem() || 
                    mainItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_TOP_BLOCK.asItem() ||
                    offItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_BOTTOM_BLOCK.asItem() || 
                    offItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_TOP_BLOCK.asItem()) {
                    
                    // Box indicator
                    level.sendParticles(ParticleTypes.END_ROD, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
                    
                    // Calculate path using A* through water
                    val target = entity.topPos
                    if (target != null) {
                        entity.pathUpdateTicks++
                        if (entity.cachedPath == null || entity.pathUpdateTicks >= 100) {
                            entity.pathUpdateTicks = 0
                            entity.cachedPath = entity.findWaterPath(level, pos, target)
                        }

                        val path = entity.cachedPath
                        if (path != null && path.size > 1 && level.random.nextInt(3) == 0) {
                            for (i in 0 until path.size - 1) {
                                val p1 = path[i]
                                val p2 = path[i + 1]
                                val dx = p2.x - p1.x
                                val dy = p2.y - p1.y
                                val dz = p2.z - p1.z
                                val dist = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                                if (dist > 0) {
                                    val steps = Math.max(2, (dist * 2).toInt())
                                    for (step in 0..steps) {
                                        val f = step.toDouble() / steps.toDouble()
                                        val px = p1.x + 0.5 + dx * f
                                        val py = p1.y + 0.5 + dy * f
                                        val pz = p1.z + 0.5 + dz * f
                                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
                                    }
                                }
                            }
                        } else if (path == null && level.random.nextInt(10) == 0) {
                            // If blocked, show an indicator
                            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                }
            }

            if (com.toancao.pokemonai.events.DragonGateEvent.currentPhase != com.toancao.pokemonai.events.DragonGateEvent.EventPhase.SWIMMING) return

            // Every 100 ticks (5 seconds) during the event
            if (level.server.tickCount % 100 != 0) return

            entity.spawnMagikarps(level)
        }
    }



    private fun spawnMagikarps(level: ServerLevel) {
        val startPos = this.blockPos
        val endPos = this.topPos ?: return // Needs a top block to spawn

        var topR10 = 0
        var topR100 = 0

        // Check Top constraints
        val topBounds = net.minecraft.world.phys.AABB(endPos).inflate(100.0)
        val topEntities = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java, topBounds) { e ->
            com.toancao.pokemonai.compat.CobblemonBridge.checkIsPokemonEntity(e) && e.tags.contains("dragon_gate_challenger")
        }

        for (p in topEntities) {
            val distSqr = p.distanceToSqr(endPos.x + 0.5, p.y, endPos.z + 0.5)
            if (distSqr <= 100.0) { // R=10 (10^2 = 100)
                topR10++
                topR100++
            } else if (distSqr <= 10000.0) { // R=100 (100^2 = 10000)
                topR100++
            }
        }

        // Check Bottom constraints
        val bottomBounds = net.minecraft.world.phys.AABB(startPos).inflate(10.0)
        val bottomEntities = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java, bottomBounds) { e ->
            com.toancao.pokemonai.compat.CobblemonBridge.checkIsPokemonEntity(e) && e.tags.contains("dragon_gate_challenger")
        }
        val bottomCount = bottomEntities.size

        if (topR10 >= 20) return
        if (topR100 >= 40) return
        if (bottomCount > 10) return

        val amount = 3 // Exactly 3 per 5 seconds

        for (i in 0 until amount) {
            var spawnPos = startPos
            for (attempt in 0..9) {
                val ox = level.random.nextInt(31) - 15
                val oz = level.random.nextInt(31) - 15
                val checkPos = startPos.offset(ox, 0, oz)
                for (dy in 5 downTo -5) {
                    val p = checkPos.above(dy)
                    if (level.getFluidState(p).`is`(net.minecraft.tags.FluidTags.WATER) &&
                        !level.getFluidState(p.above()).`is`(net.minecraft.tags.FluidTags.WATER)
                    ) {
                        spawnPos = p
                        break
                    }
                }
                if (spawnPos != startPos) break
            }

            // Complex Rarity logic:
            val isSuperRare = level.random.nextFloat() < 0.001f
            val isShiny = isSuperRare || level.random.nextFloat() < 0.01f

            val spawnLevel = if (isSuperRare) {
                40
            } else {
                val r1 = level.random.nextInt(11)
                val r2 = level.random.nextInt(11)
                20 + Math.min(r1, r2)
            }

            val shinyStr = if (isShiny) "yes" else "no"
            val propsStr = "magikarp level=$spawnLevel shiny=$shinyStr"
            val props = PokemonProperties.Companion.parse(propsStr)
            val pokemonEntity = com.toancao.pokemonai.compat.CobblemonBridge.createEntity(props, level) ?: continue

            val offsetX = (level.random.nextDouble() - 0.5) * 2
            val offsetZ = (level.random.nextDouble() - 0.5) * 2
            com.toancao.pokemonai.compat.CobblemonBridge.setPos(pokemonEntity, spawnPos.x + 0.5 + offsetX, spawnPos.y.toDouble(), spawnPos.z + 0.5 + offsetZ)

            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "dragon_gate_challenger")
            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "start_x_${spawnPos.x}")
            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "start_y_${spawnPos.y}")
            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "start_z_${spawnPos.z}")
            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "target_x_${endPos.x}")
            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "target_y_${endPos.y}")
            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "target_z_${endPos.z}")

            level.addFreshEntity(pokemonEntity as net.minecraft.world.entity.Entity)
        }
    }
}
