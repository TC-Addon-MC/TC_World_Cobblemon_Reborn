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
        val l = level
        if (l is ServerLevel) {
            com.toancao.pokemonai.utils.ParticleUtils.clearTornado(l, worldPosition)
        }
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
                val totalTicks = com.toancao.pokemonai.config.MagikarpConfigManager.config.evolvingPhaseDurationTicks
                val elapsed = totalTicks - ticksRemaining

                if (elapsed == 0) com.toancao.pokemonai.utils.ParticleUtils.createTornado(level, pos)
                com.toancao.pokemonai.utils.ParticleUtils.updateTornado(level, pos, elapsed)
                if (ticksRemaining <= 1) com.toancao.pokemonai.utils.ParticleUtils.clearTornado(level, pos)

                // Evolve exponentially using predefined accelerating thresholds within 600 ticks
                val thresholds = listOf(100, 200, 280, 340, 390, 430, 460, 485, 505, 520, 535, 545, 555, 565, 575, 585, 595)
                val stepIndex = thresholds.indexOf(elapsed)
                if (stepIndex != -1) {
                    val evolveAmount = 1 shl stepIndex // 1, 2, 4, 8, 16...

                    val radius = com.toancao.pokemonai.config.MagikarpConfigManager.config.maxMagikarpEvolutionRadius
                    val magikarps = findWaitingMagikarps(level, pos, radius)
                    var evolved = 0
                    for (mk in magikarps) {
                        if (evolved >= evolveAmount) break
                        evolveMagikarp(level, mk)
                        evolved++
                    }
                }

                // Force evolve all remaining at the very end (last 5 ticks)
                if (ticksRemaining == 5) {
                    val radius = com.toancao.pokemonai.config.MagikarpConfigManager.config.maxMagikarpEvolutionRadius
                    val magikarps = findWaitingMagikarps(level, pos, radius)
                    for (mk in magikarps) {
                        evolveMagikarp(level, mk)
                    }
                }
            }
        }

        private fun findWaitingMagikarps(level: ServerLevel, pos: BlockPos, radius: Double): List<LivingEntity> {
            val bounds = AABB(pos).inflate(radius)
            return level.getEntitiesOfClass(LivingEntity::class.java, bounds) { e ->
                com.toancao.pokemonai.compat.CobblemonBridge.checkIsPokemonEntity(e) && e.tags.contains("waiting_for_evolution") && e.tags.contains("evolution_eligible")
            }.filter {
                it.distanceToSqr(pos.x + 0.5, it.y, pos.z + 0.5) <= radius * radius
            }
        }

        private fun evolveMagikarp(level: ServerLevel, entity: LivingEntity) {
            val pokemonEntity = com.toancao.pokemonai.compat.CobblemonBridge.castToPokemonEntity(entity)
            val pokemonData = com.toancao.pokemonai.compat.CobblemonBridge.getPokemonData(pokemonEntity)
            
            val requiredLevel = com.toancao.pokemonai.config.MagikarpConfigManager.config.requiredLevelForEvolution
            if (pokemonData.level >= requiredLevel) {
                // Nhảy lên cao (vận tốc ~1.2 đến 1.5 sẽ bay lên khoảng 4-6 block)
                val baseVel = com.toancao.pokemonai.config.MagikarpConfigManager.config.jumpVelocityMin
                val maxOffset = com.toancao.pokemonai.config.MagikarpConfigManager.config.jumpVelocityMaxOffset
                var jumpVel = (baseVel + level.random.nextDouble() * maxOffset).toFloat()

                // Bắn Event
                jumpVel = com.toancao.pokemonai.api.PokemonAIEvents.ON_MAGIKARP_JUMP.invoker().onMagikarpJump(pokemonEntity, jumpVel)
                if (jumpVel <= 0f) return // Bị mod khác cancel nhảy/tiến hóa

                entity.deltaMovement = entity.deltaMovement.add(0.0, jumpVel.toDouble(), 0.0)
                entity.hasImpulse = true
                entity.hurtMarked = true
                
                // Chờ 15 tick cho cá nhảy tới đỉnh rồi mới hóa rồng
                com.toancao.pokemonai.evolution.EvolutionManager.scheduleTask(15) {
                    level.playSound(null, entity.blockPosition(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.NEUTRAL, 5.0f, 0.8f)
                    com.toancao.pokemonai.evolution.EvolutionManager.forceEvolve(pokemonEntity, "gyarados")
                    
                    entity.removeTag("waiting_for_evolution")
                    
                    // Explode roof right when evolving
                com.toancao.pokemonai.evolution.EvolutionManager.scheduleTask(50) {
                    for (dx in -3..3) {
                        for (dy in 0..15) {
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
                }
            } else {
                // If it's somehow below level 20, it fails the challenge
                entity.removeTag("waiting_for_evolution")
                entity.removeTag("dragon_gate_challenger")
            }
        }

    }
}
