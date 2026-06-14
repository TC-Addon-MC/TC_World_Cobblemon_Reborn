package com.toancao.pokemonai.blocks.entity

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.toancao.pokemonai.registry.BlockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.core.particles.ParticleTypes

class TcTopBottomBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(BlockRegistry.TC_TOP_BOTTOM_BLOCK_ENTITY, pos, state) {
    var ticksLived = 0

    companion object {
        fun tick(level: Level, pos: BlockPos, state: BlockState, entity: TcTopBottomBlockEntity) {
            if (level !is ServerLevel) return
            if (entity.isRemoved) return
            
            entity.ticksLived++
            
            // Wait a few ticks to ensure the entire structure is generated
            if (entity.ticksLived < 5) return
            
            if (level.getBlockState(pos).block !== BlockRegistry.TC_TOP_BOTTOM_BLOCK) return
            
            println("[TcTopBottom] Block at $pos is starting to process structure!")
            
            // Master block takes control and processes the whole structure
            processStructure(level, pos)
        }
        
        private fun processStructure(level: ServerLevel, origin: BlockPos) {
            val radius = 48
            val foundBlocks = mutableListOf<BlockPos>()
            
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    for (z in -radius..radius) {
                        val p = origin.offset(x, y, z)
                        val state = level.getBlockState(p)
                        if (state.block === BlockRegistry.TC_TOP_BOTTOM_BLOCK) {
                            foundBlocks.add(p)
                        }
                    }
                }
            }
            
            if (foundBlocks.isEmpty()) {
                println("[TcTopBottom] No blocks found in radius!")
                return
            }
            
            println("[TcTopBottom] Found ${foundBlocks.size} blocks in radius.")
            
            val topY = foundBlocks.maxOf { it.y }
            val bottomY = foundBlocks.minOf { it.y }
            
            println("[TcTopBottom] Bottom Y: $bottomY, Top Y: $topY")
            
            var tops = foundBlocks.filter { topY - it.y <= 5 }.toMutableList()
            var bottoms = foundBlocks.filter { it.y - bottomY <= 5 && !tops.contains(it) }.toMutableList()
            
            if (topY - bottomY <= 10) {
                // If the height difference is small, we assume there are only top blocks.
                tops = foundBlocks.toMutableList()
                bottoms.clear()
            }
            
            // Remove all tc_top_bottom blocks first
            for (p in foundBlocks) {
                level.removeBlock(p, false)
            }
            
            val actualBottoms = mutableListOf<BlockPos>()
            if (bottoms.isEmpty()) {
                // Calculate bottom block positions based on the top block, using the provided sample offsets
                // Top: (0, 21, 1) -> Bottom 1: (32, 3, 4) => dx=32, dy=-18, dz=3
                // Top: (0, 21, 1) -> Bottom 2: (-31, 3, 11) => dx=-31, dy=-18, dz=10
                for (top in tops) {
                    val b1 = top.offset(32, -18, 3)
                    val b2 = top.offset(-31, -18, 10)
                    actualBottoms.add(b1)
                    actualBottoms.add(b2)
                }
            } else {
                actualBottoms.addAll(bottoms)
            }
            
            // Place top blocks
            for (p in tops) {
                println("[TcTopBottom] Placing TOP at $p")
                level.setBlock(p, BlockRegistry.DRAGON_GATE_TOP_BLOCK.defaultBlockState(), 3)
                level.sendParticles(ParticleTypes.EXPLOSION, p.x + 0.5, p.y + 0.5, p.z + 0.5, 5, 0.5, 0.5, 0.5, 0.1)
            }
            
            // Place bottom blocks and connect them
            for (p in actualBottoms) {
                println("[TcTopBottom] Placing BOTTOM at $p")
                level.setBlock(p, BlockRegistry.DRAGON_GATE_BOTTOM_BLOCK.defaultBlockState(), 3)
                level.sendParticles(ParticleTypes.EXPLOSION, p.x + 0.5, p.y + 0.5, p.z + 0.5, 5, 0.5, 0.5, 0.5, 0.1)
                
                // For each bottom, find nearest top
                val nearestTop = tops.minByOrNull { it.distSqr(p) }
                if (nearestTop != null) {
                    // Calculate path and place waypoints
                    val firstTarget = calculateAndPlaceWaypoints(level, p, nearestTop)
                    
                    // Link the bottom to the first target (which could be the first waypoint, or the top if no waypoints)
                    val be = level.getBlockEntity(p)
                    if (be is DragonGateBottomBlockEntity) {
                        be.updateTopPos(firstTarget)
                    }
                }
            }
        }
        
        // Returns the first target (either the first waypoint, or the endPos if no waypoints)
        private fun calculateAndPlaceWaypoints(level: ServerLevel, start: BlockPos, end: BlockPos): BlockPos {
            val path = findWaterPath(level, start, end) ?: return end
            
            val waypointSpaced = mutableListOf<BlockPos>()
            var step = 0
            for (i in 1 until path.size - 1) {
                step++
                if (step >= 4) { // Place a waypoint every 4 blocks
                    waypointSpaced.add(path[i])
                    step = 0
                }
            }
            
            if (waypointSpaced.isEmpty()) return end
            
            for (i in waypointSpaced.indices) {
                val wp = waypointSpaced[i]
                level.setBlock(wp, BlockRegistry.DRAGON_GATE_WAYPOINT_BLOCK.defaultBlockState(), 3)
                level.sendParticles(ParticleTypes.END_ROD, wp.x + 0.5, wp.y + 0.5, wp.z + 0.5, 3, 0.2, 0.2, 0.2, 0.05)
                
                val nextPos = if (i + 1 < waypointSpaced.size) waypointSpaced[i + 1] else end
                val be = level.getBlockEntity(wp)
                if (be is DragonGateWaypointBlockEntity) {
                    be.updateNextPos(nextPos)
                }
            }
            
            return waypointSpaced.first()
        }
        
        private fun findWaterPath(level: ServerLevel, start: BlockPos, end: BlockPos): List<BlockPos>? {
            val maxNodes = 5000
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
