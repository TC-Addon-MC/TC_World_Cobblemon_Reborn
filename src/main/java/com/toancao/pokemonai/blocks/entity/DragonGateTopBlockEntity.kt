package com.toancao.pokemonai.blocks.entity

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.entity.LivingEntity
import com.toancao.pokemonai.events.DragonGateEvent
import com.toancao.pokemonai.registry.BlockRegistry

class DragonGateTopBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(BlockRegistry.DRAGON_GATE_TOP_BLOCK_ENTITY, pos, state) {

    init {
        DragonGateEvent.topBlocks.add(pos)
    }

    override fun setRemoved() {
        super.setRemoved()
        DragonGateEvent.topBlocks.remove(worldPosition)
    }

    override fun loadAdditional(tag: net.minecraft.nbt.CompoundTag, provider: net.minecraft.core.HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        DragonGateEvent.topBlocks.add(worldPosition)
    }

    companion object {
        fun tick(level: Level, pos: BlockPos, state: BlockState, entity: DragonGateTopBlockEntity) {
            if (level !is ServerLevel) return

            val phase = DragonGateEvent.currentPhase
            if (phase == DragonGateEvent.EventPhase.EVOLVING) {
                val ticksRemaining = DragonGateEvent.phaseTicks
                val elapsed = 600 - ticksRemaining

                // Generate Tornado every 15 ticks
                if (level.server.tickCount % 15 == 0) {
                    createTornado(level, pos)
                }

                // Evolve exponentially using predefined accelerating thresholds within 600 ticks
                val thresholds = listOf(100, 200, 280, 340, 390, 430, 460, 485, 505, 520, 535, 545, 555, 565, 575, 585, 595)
                val stepIndex = thresholds.indexOf(elapsed)
                if (stepIndex != -1) {
                    val evolveAmount = 1 shl stepIndex // 1, 2, 4, 8, 16...

                    val magikarps = findWaitingMagikarps(level, pos, 10.0)
                    var evolved = 0
                    for (mk in magikarps) {
                        if (evolved >= evolveAmount) break
                        evolveMagikarp(level, mk)
                        evolved++
                    }
                }

                // Force evolve all remaining at the very end (last 5 ticks)
                if (ticksRemaining == 5) {
                    val magikarps = findWaitingMagikarps(level, pos, 10.0)
                    for (mk in magikarps) {
                        evolveMagikarp(level, mk)
                    }
                }
            } else if (phase == DragonGateEvent.EventPhase.FREE_SWIM) {
                // If the phase transitioned and we still have waiting magikarps (maybe they were out of R=10)
                // We should remove their waiting tag if they didn't evolve.
                if (DragonGateEvent.phaseTicks == 1199) { // Just at the start of FREE_SWIM
                    val bounds = AABB(pos).inflate(40.0)
                    val allMks = level.getEntitiesOfClass(LivingEntity::class.java, bounds) { e ->
                        com.toancao.pokemonai.compat.CobblemonBridge.checkIsPokemonEntity(e) && e.tags.contains("waiting_for_evolution")
                    }
                    for (mk in allMks) {
                        mk.removeTag("waiting_for_evolution")
                        mk.removeTag("dragon_gate_challenger") // Revoke their right to evolve
                    }
                }
            }
        }

        private fun findWaitingMagikarps(level: ServerLevel, pos: BlockPos, radius: Double): List<LivingEntity> {
            val bounds = AABB(pos).inflate(radius)
            return level.getEntitiesOfClass(LivingEntity::class.java, bounds) { e ->
                com.toancao.pokemonai.compat.CobblemonBridge.checkIsPokemonEntity(e) && e.tags.contains("waiting_for_evolution")
            }.filter {
                it.distanceToSqr(pos.x + 0.5, it.y, pos.z + 0.5) <= radius * radius
            }
        }

        private fun evolveMagikarp(level: ServerLevel, entity: LivingEntity) {
            val pokemonEntity = com.toancao.pokemonai.compat.CobblemonBridge.castToPokemonEntity(entity)
            val pokemonData = com.toancao.pokemonai.compat.CobblemonBridge.getPokemonData(pokemonEntity)
            
            if (pokemonData.level >= 20) {
                level.playSound(null, entity.blockPosition(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.NEUTRAL, 5.0f, 0.8f)
                com.toancao.pokemonai.evolution.EvolutionManager.forceEvolve(pokemonEntity, "gyarados")
                
                entity.removeTag("waiting_for_evolution")
                entity.addTag("gyarados_resting")
                
                // Explode roof right when evolving
                com.toancao.pokemonai.evolution.EvolutionManager.scheduleTask(50) {
                    for (dx in -3..3) {
                        for (dy in 0..8) {
                            for (dz in -3..3) {
                                val bp = entity.blockPosition().offset(dx, dy, dz)
                                val state = level.getBlockState(bp)
                                if (!state.isAir && state.block != BlockRegistry.DRAGON_GATE_TOP_BLOCK) {
                                    level.destroyBlock(bp, true)
                                }
                            }
                        }
                    }
                }
            } else {
                // If it's somehow below level 20, it fails the challenge
                entity.removeTag("waiting_for_evolution")
                entity.removeTag("dragon_gate_challenger")
            }
        }

        private fun createTornado(level: ServerLevel, pos: BlockPos) {
            val maxHeight = 30.0
            val maxRadius = 8.0
            val loops = 15.0

            for (yOffset in 0..(maxHeight * 4).toInt()) {
                val h = yOffset / 4.0
                val progress = h / maxHeight
                
                val currentRadius = 0.5 + (maxRadius - 0.5) * Math.pow(progress, 3.0) 
                val angle = progress * Math.PI * 2.0 * loops
                
                val px1 = pos.x + 0.5 + currentRadius * Math.cos(angle)
                val pz1 = pos.z + 0.5 + currentRadius * Math.sin(angle)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, px1, pos.y + h, pz1, 5, 0.5, 0.2, 0.5, 0.1)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, px1, pos.y + h, pz1, 2, 0.2, 0.2, 0.2, 0.05)
                
                val px2 = pos.x + 0.5 + currentRadius * Math.cos(angle + Math.PI)
                val pz2 = pos.z + 0.5 + currentRadius * Math.sin(angle + Math.PI)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, px2, pos.y + h, pz2, 5, 0.5, 0.2, 0.5, 0.1)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, px2, pos.y + h, pz2, 1, 0.2, 0.2, 0.2, 0.02)
            }
            
            for (i in 0..(maxHeight * 2).toInt()) {
                val h = i / 2.0
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, pos.x + 0.5, pos.y + h, pos.z + 0.5, 10, 0.3, 1.0, 0.3, 0.5)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, pos.x + 0.5, pos.y + h, pos.z + 0.5, 5, 0.5, 1.0, 0.5, 0.2)
            }
        }
    }
}
