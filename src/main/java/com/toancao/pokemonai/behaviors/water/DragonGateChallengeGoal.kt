@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.particles.ParticleTypes

class DragonGateChallengeGoal(private val pokemonEntity: PokemonEntity) : Goal() {
    private val entity: LivingEntity = pokemonEntity as LivingEntity
    private var targetPos: BlockPos? = null

    private var stamina = 0f
    private var restTicks = 0
    private var arrivedTicks = 0
    private var leapCooldown = 0
    private var isLeaping = false
    private var challengeTicks = 0

    private val maxStamina: Float
        get() = (entity.maxHealth * 10f).toFloat()

    private val swimSpeed: Double
        get() = entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 0.2

    private val surfaceSpeed: Double
        get() = entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 0.3

    private val leapForce: Double
        get() {
            val attack = entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            val sizeWeight = entity.bbWidth * entity.bbHeight
            val statFactor = (attack / Math.max(sizeWeight.toDouble(), 0.1)) * 0.05
            return Math.min(1.0, Math.max(0.4, 0.4 + statFactor))
        }

    companion object {
        private const val STAMINA_DRAIN = 1.2f
        private const val STAMINA_REGEN = 0.4f
        private const val REST_DURATION = 60
        private const val ARRIVE_WAIT = 100
        private const val LEAP_COOLDOWN_MIN = 80
        private const val LEAP_COOLDOWN_MAX = 160
        private const val SURFACE_THRESHOLD = 0.8
    }

    override fun canUse(): Boolean {
        if (!entity.tags.contains("dragon_gate_challenger")) return false
        val species = com.toancao.pokemonai.compat.CobblemonBridge.getSpeciesName(pokemonEntity)
        if (species != "magikarp") return false
        targetPos = resolveTarget(entity) ?: return false
        return true
    }

    override fun canContinueToUse() = canUse()

    override fun start() {
        stamina = maxStamina
        restTicks = 0
        arrivedTicks = 0
        challengeTicks = 0
        leapCooldown = (LEAP_COOLDOWN_MIN + entity.level().random.nextInt(LEAP_COOLDOWN_MAX - LEAP_COOLDOWN_MIN))
    }

    override fun tick() {
        val target = targetPos ?: return
        val level = entity.level() as? ServerLevel ?: return

        challengeTicks++
        
        val distSqrToTarget = entity.distanceToSqr(target.x.toDouble() + 0.5, target.y.toDouble() + 0.5, target.z.toDouble() + 0.5)
        
        // Nếu đã được nhận thẻ chờ tiến hóa ở đỉnh tháp
        if (entity.tags.contains("waiting_for_evolution")) {
            if (distSqrToTarget <= 100.0) {
                // Đang trong R=10, thả rông hoàn toàn (không chạy tiếp lệnh ép bơi bên dưới)
                return
            }
            // Nếu trôi ra > 10, để cho code trôi xuống dưới. Hệ thống bơi lội (deltaMovement) 
            // của chính Goal này sẽ kích hoạt và bắp nó bơi hì hục về lại đích y như lúc vượt thác!
        }

        if (restTicks > 0) {
            restTicks--
            stamina = (stamina + STAMINA_REGEN).coerceAtMost(maxStamina)
            if (entity.isInWater) {
                entity.deltaMovement = Vec3(
                    entity.deltaMovement.x * 0.7,
                    0.01,
                    entity.deltaMovement.z * 0.7
                )
            } else {
                entity.deltaMovement = entity.deltaMovement.add(0.0, -0.04, 0.0)
            }
            return
        }

        val dx = target.x - entity.x
        val dy = target.y - entity.y
        val dz = target.z - entity.z
        val distHoriz = Math.sqrt(dx * dx + dz * dz)
        val arrived = distHoriz < 2.5 && entity.y >= target.y - 1.5

        if (arrived) {
            val be = level.getBlockEntity(target)
            if (be is com.toancao.pokemonai.blocks.entity.DragonGateWaypointBlockEntity) {
                val nextPos = be.nextPos
                if (nextPos != null) {
                    // Update tags
                    val tagsToRemove = entity.tags.filter { it.startsWith("target_") }
                    tagsToRemove.forEach { entity.removeTag(it) }
                    
                    com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "target_x_${nextPos.x}")
                    com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "target_y_${nextPos.y}")
                    com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "target_z_${nextPos.z}")
                    
                    this.targetPos = nextPos
                    this.arrivedTicks = 0
                    
                    level.sendParticles(ParticleTypes.END_ROD, entity.x, entity.y, entity.z, 20, 0.5, 0.5, 0.5, 0.1)
                    return
                }
            }

            arrivedTicks++
            entity.deltaMovement = entity.deltaMovement.scale(0.6)

            if (arrivedTicks >= ARRIVE_WAIT) {
                if (level.getBlockState(target).block is com.toancao.pokemonai.blocks.DragonGateTopBlock) {
                    val distSqr = entity.distanceToSqr(target.x.toDouble() + 0.5, target.y.toDouble() + 0.5, target.z.toDouble() + 0.5)
                    if (distSqr <= 100.0) { // R=10
                        // Đánh dấu đã đến đỉnh và roll 50% cơ hội tiến hóa
                        if (!entity.tags.contains("waiting_for_evolution")) {
                            com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "waiting_for_evolution")
                            if (level.random.nextFloat() < 0.90f) {
                                com.toancao.pokemonai.compat.CobblemonBridge.addTag(pokemonEntity, "evolution_eligible")
                            }
                        }
                    } else {
                        // Slowly move closer
                        entity.deltaMovement = Vec3(Math.cos(challengeTicks * 0.1) * 0.05, 0.0, Math.sin(challengeTicks * 0.1) * 0.05)
                    }
                }
            }
            return
        }

        arrivedTicks = 0

        if (entity.isInWater) {
            val waterSurface = findWaterSurface(entity)
            val distToSurface = waterSurface - entity.y

            val needsToSurface = entity.y < (entity.level().getSeaLevel() - 0.5) && dy < 3

            if (leapCooldown > 0) leapCooldown--

            if (isLeaping) {
                isLeaping = false
            } else if (!needsToSurface && leapCooldown == 0 && distToSurface < SURFACE_THRESHOLD) {
                val horizontal = Vec3(dx, 0.0, dz).normalize()
                entity.deltaMovement = Vec3(
                    horizontal.x * swimSpeed * 1.5,
                    leapForce,
                    horizontal.z * swimSpeed * 1.5
                )
                isLeaping = true
                leapCooldown = (LEAP_COOLDOWN_MIN + entity.level().random.nextInt(LEAP_COOLDOWN_MAX - LEAP_COOLDOWN_MIN))

                level.sendParticles(ParticleTypes.SPLASH, entity.x, entity.y, entity.z, 20, 0.4, 0.1, 0.4, 0.2)
                level.sendParticles(ParticleTypes.BUBBLE, entity.x, entity.y, entity.z, 10, 0.3, 0.0, 0.3, 0.1)

            } else if (needsToSurface) {
                entity.deltaMovement = Vec3(
                    entity.deltaMovement.x * 0.8,
                    surfaceSpeed,
                    entity.deltaMovement.z * 0.8
                )
            } else {
                val horizontal = Vec3(dx, 0.0, dz).normalize()
                val verticalPull = (dy * 0.05).coerceIn(-0.05, swimSpeed)
                entity.deltaMovement = Vec3(
                    horizontal.x * swimSpeed,
                    verticalPull + 0.01,
                    horizontal.z * swimSpeed
                )

                if (dy > 0.5) {
                    stamina -= STAMINA_DRAIN
                    if (level.random.nextInt(4) == 0) {
                        level.sendParticles(ParticleTypes.SPLASH, entity.x, entity.y, entity.z, 3, 0.3, 0.2, 0.3, 0.05)
                    }
                }

                if (stamina <= 0f) {
                    stamina = 0f
                    restTicks = REST_DURATION
                }
            }
        } else {
            val horizontal = Vec3(dx, 0.0, dz).normalize()
            entity.deltaMovement = entity.deltaMovement.add(
                horizontal.x * 0.01,
                -0.04,
                horizontal.z * 0.01
            )
        }

        entity.lookAt(
            net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
            Vec3(target.x.toDouble(), target.y.toDouble(), target.z.toDouble())
        )
        entity.yBodyRot = entity.yRotO
    }

    private fun resolveTarget(entity: LivingEntity): BlockPos? {
        var tx: Int? = null; var ty: Int? = null; var tz: Int? = null
        for (tag in entity.tags) {
            if (tag.startsWith("target_x_")) tx = tag.substringAfter("target_x_").toIntOrNull()
            if (tag.startsWith("target_y_")) ty = tag.substringAfter("target_y_").toIntOrNull()
            if (tag.startsWith("target_z_")) tz = tag.substringAfter("target_z_").toIntOrNull()
        }
        return if (tx != null && ty != null && tz != null) BlockPos(tx, ty, tz) else null
    }

    private fun findWaterSurface(entity: LivingEntity): Double {
        val level = entity.level()
        var y = entity.blockY
        repeat(10) {
            val pos = BlockPos(entity.blockX, y + 1, entity.blockZ)
            if (!level.getFluidState(pos).`is`(net.minecraft.tags.FluidTags.WATER)) return y.toDouble()
            y++
        }
        return y.toDouble()
    }

}
