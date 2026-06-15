package com.toancao.pokemonai.utils

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Display.BlockDisplay
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import com.mojang.math.Transformation
import org.joml.Vector3f
import org.joml.Quaternionf
import net.minecraft.core.particles.ParticleTypes
import kotlin.math.*
import kotlin.random.Random

object ParticleUtils {

    private data class DebrisEntity(
        val entityId: Int,
        var heightProgress: Double,
        var angleOffset: Double,
        val speedMultiplier: Double
    )

    private data class TornadoState(
        val debris: List<DebrisEntity>,
        var globalAngle: Double = 0.0
    )

    private val activeTornados = mutableMapOf<BlockPos, TornadoState>()

    // === Shape ===
    private const val MAX_HEIGHT = 30.0
    private const val MAX_RADIUS = 8.0
    private const val WAIST_HEIGHT = 0.4   // 40% từ dưới lên = eo hẹp nhất
    private const val WAIST_RADIUS = 1.2   // eo hẹp nhất

    private fun radiusAt(progress: Double): Double {
        // Hyperbola: rộng đáy (progress=0), hẹp ở WAIST_HEIGHT, rộng nhẹ ở đỉnh
        val t = progress - WAIST_HEIGHT
        return WAIST_RADIUS + (MAX_RADIUS - WAIST_RADIUS) * (t * t / (WAIST_HEIGHT * WAIST_HEIGHT)).coerceAtMost(1.0)
    }

    private fun spawnDisplay(
        level: ServerLevel,
        x: Double, y: Double, z: Double,
        state: BlockState,
        scale: Float
    ): Int {
        val e = BlockDisplay(EntityType.BLOCK_DISPLAY, level)
        e.setPos(x - scale / 2.0, y - scale / 2.0, z - scale / 2.0)
        e.blockState = state
        e.setTransformation(Transformation(
            Vector3f(0f, 0f, 0f), null,
            Vector3f(scale, scale, scale), null
        ))
        e.addTag("tornado_debris")
        level.addFreshEntity(e)
        return e.id
    }

    fun createTornado(level: ServerLevel, pos: BlockPos) {
        if (activeTornados.containsKey(pos)) return

        val debrisList = mutableListOf<DebrisEntity>()
        val cx = pos.x + 0.5
        val cz = pos.z + 0.5
        val baseY = pos.y.toDouble()
        
        val blocks = listOf(Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.ICE)
        
        // Spawn 4-7 debris entities to swirl in the tornado
        val debrisCount = Random.nextInt(4, 8)
        for (i in 0 until debrisCount) {
            val progress = Random.nextDouble()
            val h = progress * MAX_HEIGHT
            val radius = radiusAt(progress)
            val angle = Random.nextDouble() * 2 * PI
            
            val state = blocks.random().defaultBlockState()
            val id = spawnDisplay(level,
                cx + radius * cos(angle), baseY + h, cz + radius * sin(angle),
                state, 0.4f)
            
            debrisList.add(DebrisEntity(id, progress, angle, Random.nextDouble(0.8, 1.2)))
        }

        activeTornados[pos] = TornadoState(debrisList)
    }

    fun updateTornado(level: ServerLevel, pos: BlockPos, tick: Int) {
        val state = activeTornados[pos] ?: return
        state.globalAngle += 0.2

        val cx = pos.x + 0.5
        val cz = pos.z + 0.5
        val baseY = pos.y.toDouble()

        // --- 1. PARTICLES (Main visual, sinh ra liên tục) ---
        // Vẽ lại hình hyperbola bằng particle
        val particlesPerTick = 20
        for (i in 0 until particlesPerTick) {
            val progress = Random.nextDouble()
            val h = progress * MAX_HEIGHT
            val radius = radiusAt(progress)
            
            // Góc xoáy cộng thêm độ nhiễu
            val angle = state.globalAngle + (h * 0.2) + Random.nextDouble() * 2 * PI
            
            val px = cx + radius * cos(angle)
            val pz = cz + radius * sin(angle)
            
            // Mist (hạt mây)
            if (Random.nextBoolean()) {
                level.sendParticles(ParticleTypes.CLOUD, px, baseY + h, pz, 1, 0.2, 0.2, 0.2, 0.02)
            }
            // Inner water (hạt nước bắn tóe)
            level.sendParticles(ParticleTypes.SPLASH, px, baseY + h, pz, 2, 0.3, 0.2, 0.3, 0.05)
            
            // Cột nước trung tâm (rơi xuống)
            if (progress < 0.5 && Random.nextFloat() < 0.3f) {
                level.sendParticles(ParticleTypes.FALLING_WATER, cx + (Random.nextDouble()-0.5), baseY + h, cz + (Random.nextDouble()-0.5), 2, 0.2, 0.5, 0.2, 0.1)
            }
        }
        
        // Base spray (tóe nước ở chân vòi rồng)
        for (i in 0..5) {
            val angle = Random.nextDouble() * 2 * PI
            val sr = Random.nextDouble() * MAX_RADIUS
            level.sendParticles(ParticleTypes.SPLASH, cx + sr * cos(angle), baseY + Random.nextDouble(), cz + sr * sin(angle), 5, 0.2, 0.2, 0.2, 0.1)
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx + sr * cos(angle), baseY + Random.nextDouble(), cz + sr * sin(angle), 1, 0.2, 0.2, 0.2, 0.05)
        }

        // --- 2. UPDATE DEBRIS ENTITIES (4-7 blocks bay lơ lửng) ---
        // Để mượt thì update entity mỗi tick, nhưng do ít entity (4-7) nên sẽ không bị lag
        val entityMap = mutableMapOf<Int, BlockDisplay>()
        level.getEntities(EntityType.BLOCK_DISPLAY, net.minecraft.world.phys.AABB(pos).inflate(40.0)) {
            it.tags.contains("tornado_debris")
        }.forEach { entityMap[it.id] = it }
        
        for (debris in state.debris) {
            val entity = entityMap[debris.entityId] ?: continue
            
            // Hút dần lên trên
            debris.heightProgress += 0.005 * debris.speedMultiplier
            if (debris.heightProgress > 1.0) {
                debris.heightProgress = 0.0 // Lên tới đỉnh thì rớt lại xuống đáy
            }
            
            val progress = debris.heightProgress
            val h = progress * MAX_HEIGHT
            val radius = radiusAt(progress)
            
            // Quay quanh trục, hẹp thì quay nhanh (bảo toàn mô men)
            val angularSpeed = 0.15 * (WAIST_RADIUS / radius.coerceAtLeast(0.5)) * debris.speedMultiplier
            debris.angleOffset += angularSpeed
            
            val nx = cx + radius * cos(debris.angleOffset)
            val nz = cz + radius * sin(debris.angleOffset)
            
            // Scale to để nhìn rõ hơn (scale = 0.4)
            val scale = 0.4f
            entity.setPos(nx - scale/2, baseY + h - scale/2, nz - scale/2)
            
            // Cập nhật quay entity lộn xộn cho tự nhiên
            entity.setTransformation(Transformation(
                Vector3f(0f, 0f, 0f), 
                Quaternionf().rotationXYZ((tick * 0.1 * debris.speedMultiplier).toFloat(), (tick * 0.2 * debris.speedMultiplier).toFloat(), 0f),
                Vector3f(scale, scale, scale), null
            ))
        }
    }

    fun clearTornado(level: ServerLevel, pos: BlockPos) {
        val state = activeTornados.remove(pos) ?: return
        val ids = state.debris.map { it.entityId }.toSet()
        level.getEntities(EntityType.BLOCK_DISPLAY, net.minecraft.world.phys.AABB(pos).inflate(40.0)) {
            it.tags.contains("tornado_debris") && it.id in ids
        }.forEach { it.discard() }
    }
}
