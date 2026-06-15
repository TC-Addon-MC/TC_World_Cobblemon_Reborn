package com.toancao.pokemonai.behaviors.water

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import com.toancao.pokemonai.events.DragonGateEvent

class DragonGateJumpBackGoal(private val pokemonEntity: PokemonEntity) : Goal() {
    private val entity: LivingEntity = pokemonEntity as LivingEntity
    private var targetPos: BlockPos? = null
    private var isJumping = false
    private var hasJumped = false
    
    private var jumpDelay = -1
    private var jumpTick = 0
    private var jumpDuration = 0
    private var startPos: Vec3? = null
    private var H = 0.0

    override fun canUse(): Boolean {
        return false // Disable jump back goal so dragons act normally
    }

    override fun canContinueToUse(): Boolean {
        if (!isJumping) return false
        if (targetPos == null) return false
        if (jumpTick >= jumpDuration && jumpDuration > 0) return false
        return true
    }

    override fun start() {
        var sx: Int? = null
        var sy: Int? = null
        var sz: Int? = null
        for (tag in entity.tags) {
            if (tag.startsWith("start_x_")) sx = tag.substringAfter("start_x_").toIntOrNull()
            if (tag.startsWith("start_y_")) sy = tag.substringAfter("start_y_").toIntOrNull()
            if (tag.startsWith("start_z_")) sz = tag.substringAfter("start_z_").toIntOrNull()
        }

        if (sx != null && sy != null && sz != null) {
            targetPos = BlockPos(sx, sy, sz)
            isJumping = true
            hasJumped = true
            
            // Random delay (0-60 ticks) for sequential jumping
            jumpDelay = entity.level().random.nextInt(60)
        } else {
            // Không có tọa độ về thì giải tán luôn
            stop()
        }
    }

    override fun tick() {
        if (jumpDelay > 0) {
            jumpDelay--
            return
        }

        val target = targetPos ?: return

        if (jumpDelay == 0) {
            jumpDelay-- // Đánh dấu đã bắt đầu nhảy
            startPos = entity.position()
            
            val dx = target.x.toDouble() + 0.5 - startPos!!.x
            val dz = target.z.toDouble() + 0.5 - startPos!!.z
            val dist = Math.sqrt(dx * dx + dz * dz)
            
            // Tính số tick bay trên không
            jumpDuration = Math.max(30, (dist * 1.2).toInt())
            jumpTick = 0
            
            // Tính độ cao của đỉnh parabol (giảm độ vọt lên)
             H = Math.max(10.0, dist * 0.3)
       
            (entity.level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, entity.x, entity.y, entity.z, 20, 1.0, 0.5, 1.0, 0.2)
            (entity.level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, entity.x, entity.y, entity.z, 5, 0.5, 0.5, 0.5, 0.05)
        }

        if (jumpDuration > 0 && jumpTick < jumpDuration) {
            val t = jumpTick.toDouble() / jumpDuration.toDouble()
            val nextT = (jumpTick + 1).toDouble() / jumpDuration.toDouble()
            val st = startPos!!
            
            // Parabola eq: y(t) = y0 + (y1 - y0)*t + 4*H*t*(1-t)
            val curY = st.y + (target.y - st.y) * t + 4 * H * t * (1 - t)
            val nextY = st.y + (target.y - st.y) * nextT + 4 * H * nextT * (1 - nextT)
            
            val curX = st.x + (target.x.toDouble() + 0.5 - st.x) * t
            val nextX = st.x + (target.x.toDouble() + 0.5 - st.x) * nextT
            
            val curZ = st.z + (target.z.toDouble() + 0.5 - st.z) * t
            val nextZ = st.z + (target.z.toDouble() + 0.5 - st.z) * nextT
            
            // Bằng việc gán deltaMovement, Minecraft engine sẽ di chuyển entity đúng đoạn này, ghi đè toàn bộ lực cản
            entity.deltaMovement = Vec3(nextX - curX, nextY - curY, nextZ - curZ)
            entity.hasImpulse = true
            
            // Quay đầu theo hướng chuyển động
            val dx = nextX - curX
            val dz = nextZ - curZ
            if (dx * dx + dz * dz > 0.001) {
                entity.yRot = (Math.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90f
                entity.yBodyRot = entity.yRot
                entity.yHeadRot = entity.yRot
            }
            
            jumpTick++
        }
    }

    override fun stop() {
        isJumping = false
        val toRemove = mutableListOf<String>()
        for (tag in entity.tags) {
            if (tag.startsWith("start_") || tag.startsWith("target_") || tag == "gyarados_resting" || tag == "dragon_gate_challenger" || tag == "waiting_for_evolution") {
                toRemove.add(tag)
            }
        }
        for (tag in toRemove) {
            entity.removeTag(tag)
        }
    }
}
