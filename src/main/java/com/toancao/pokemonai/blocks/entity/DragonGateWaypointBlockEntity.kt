package com.toancao.pokemonai.blocks.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class DragonGateWaypointBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_WAYPOINT_BLOCK_ENTITY, pos, state) {
    var nextPos: BlockPos? = null
    var cachedPath: List<BlockPos>? = null
    var pathUpdateTicks = 0

    fun updateNextPos(pos: BlockPos) {
        this.nextPos = pos
        setChanged()
    }

    override fun saveAdditional(tag: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        if (nextPos != null) {
            tag.putInt("NextX", nextPos!!.x)
            tag.putInt("NextY", nextPos!!.y)
            tag.putInt("NextZ", nextPos!!.z)
        }
    }

    override fun loadAdditional(tag: CompoundTag, provider: net.minecraft.core.HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        if (tag.contains("NextX")) {
            nextPos = BlockPos(tag.getInt("NextX"), tag.getInt("NextY"), tag.getInt("NextZ"))
        }
    }

    companion object {
        fun tick(level: Level, pos: BlockPos, state: BlockState, entity: DragonGateWaypointBlockEntity) {
            if (level !is ServerLevel) return

            // Render particles for players holding the block
            val player = level.getNearestPlayer(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), 20.0, false)
            if (player != null) {
                val mainItem = player.mainHandItem.item
                val offItem = player.offhandItem.item
                
                val holdsDragonGateItem = 
                    mainItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_BOTTOM_BLOCK.asItem() || 
                    mainItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_TOP_BLOCK.asItem() ||
                    mainItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_WAYPOINT_BLOCK.asItem() ||
                    offItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_BOTTOM_BLOCK.asItem() || 
                    offItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_TOP_BLOCK.asItem() ||
                    offItem == com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_WAYPOINT_BLOCK.asItem()

                if (holdsDragonGateItem) {
                    // Box indicator (using different particle for waypoint: ENCHANT)
                    level.sendParticles(ParticleTypes.ENCHANT, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 3, 0.2, 0.2, 0.2, 0.05)
                    
                    // Calculate path using A* through water
                    val target = entity.nextPos
                    if (target != null) {
                        entity.pathUpdateTicks++
                        if (entity.cachedPath == null || entity.pathUpdateTicks >= 100) {
                            entity.pathUpdateTicks = 0
                            entity.cachedPath = findWaterPath(level, pos, target)
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
        }

        private fun findWaterPath(level: ServerLevel, start: BlockPos, end: BlockPos): List<BlockPos>? {
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
    }
}
