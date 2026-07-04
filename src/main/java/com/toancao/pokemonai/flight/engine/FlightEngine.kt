package com.toancao.pokemonai.flight.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.FlightConfig
import com.toancao.pokemonai.flight.FlightHelpers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3
import java.util.UUID

// Tầng vật lý DUY NHẤT: quản lý toàn bộ deltaMovement, gravity, flyingFlag cho mọi session bay
object FlightEngine {

    private val activeSessions = mutableMapOf<UUID, FlightSession>()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            val toRemove = mutableListOf<UUID>()
            for ((uuid, session) in activeSessions) {
                if (!session.isAlive() || session.state == InternalFlightState.DONE) {
                    toRemove.add(uuid)
                } else {
                    tickSession(session)
                    if (session.state == InternalFlightState.DONE) toRemove.add(uuid)
                }
            }
            toRemove.forEach { activeSessions.remove(it) }
        }
    }

    fun flyTo(
        pokemon: PokemonEntity,
        target: Vec3,
        hover: Boolean = false,
        config: FlightConfig = FlightConfig()
    ) {
        val existingSession = activeSessions[pokemon.uuid]
        if (existingSession != null) {
            existingSession.target = target
            existingSession.hover = hover
            existingSession.config = config
            existingSession.state = InternalFlightState.FLYING
        } else {
            val mob = pokemon as net.minecraft.world.entity.Mob
            mob.deltaMovement = Vec3.ZERO
            activeSessions[pokemon.uuid] = FlightSession(pokemon, target, hover, config)
        }
    }

    // Lệnh ép hạ cánh mượt mà xuống mặt đất
    fun land(
        pokemon: PokemonEntity,
        config: FlightConfig = FlightConfig(),
        avoidWater: Boolean = true
    ) {
        val existingSession = activeSessions[pokemon.uuid]

        val mob = pokemon as net.minecraft.world.entity.Mob
        val level = mob.level() ?: return
        
        // Hướng bay lướt xuống phía trước
        val yaw = Math.toRadians(mob.yRot.toDouble())
        val dirX = -kotlin.math.sin(yaw)
        val dirZ = kotlin.math.cos(yaw)
        
        val targetX = mob.x + dirX * 15.0
        val targetZ = mob.z + dirZ * 15.0
        val groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetX.toInt(), targetZ.toInt()).toDouble()

        
        val target = Vec3(targetX, groundY, targetZ)
        
        if (existingSession != null) {
            existingSession.target = target
            existingSession.hover = false
            existingSession.config = config
            existingSession.state = InternalFlightState.FLYING
            existingSession.isSearchingLand = avoidWater
            existingSession.needsBounce = false
        } else {
            mob.deltaMovement = Vec3.ZERO
            val newSession = FlightSession(pokemon, target, false, config)
            newSession.isSearchingLand = avoidWater
            activeSessions[pokemon.uuid] = newSession
        }
    }

    // Dừng bay ngay, trả về vật lý tự nhiên
    fun stopFlight(pokemon: PokemonEntity) {
        activeSessions.remove(pokemon.uuid)
        FlightHelpers.terminateFlight(pokemon)
    }

    fun hasActiveFlight(pokemon: PokemonEntity): Boolean = activeSessions.containsKey(pokemon.uuid)

    fun needsBounce(pokemon: PokemonEntity): Boolean = activeSessions[pokemon.uuid]?.needsBounce == true

    // Tick logic theo đúng thứ tự ưu tiên từ kế hoạch
    private fun tickSession(session: FlightSession) {
        val mob = session.pokemon as net.minecraft.world.entity.Mob
        val p = session.config
        session.ticksInCurrentState++

        // ── Bước 1: Kiểm tra điều kiện rớt/dừng ────────────────────────────
        if (p.dropOnHit && mob.hurtTime > 0) {
            terminateSession(session)
            return
        }

        val waterStatus = FlightHelpers.checkWaterStatus(mob)
        if (waterStatus == FlightHelpers.WaterStatus.SUBMERGED && p.stopWhenFullySubmerged) {
            terminateSession(session)
            return
        }
        if (waterStatus == FlightHelpers.WaterStatus.SURFACE && p.dropOnWaterSurface) {
            terminateSession(session)
            return
        }

        // ── Bước 2: Xử lý theo state hiện tại ──────────────────────────────
        when (session.state) {
            InternalFlightState.FLYING -> tickFlying(mob, session)
            InternalFlightState.ARRIVED_HOVER -> tickHovering(mob, session)
            InternalFlightState.FALLING -> tickFalling(mob, session)
            InternalFlightState.DONE -> { /* đã bị đánh dấu remove */ }
        }
    }

    private fun tickFlying(mob: net.minecraft.world.entity.Mob, session: FlightSession) {
        val level = mob.level()
        val posBelow = mob.blockPosition().below()
        val stateBelow = level.getBlockState(posBelow)
        
        val isPhysicallyOnGround = mob.onGround() || 
            (!stateBelow.isAir && !stateBelow.getCollisionShape(level, posBelow).isEmpty)

        // Chỉ tự động kết thúc nếu đang chủ động hạ cánh tìm đất
        if (session.isSearchingLand && (isPhysicallyOnGround || mob.isInWater || mob.isUnderWater)) {
            terminateSession(session)
            return
        }

        // Nếu đang trong chế độ hạ cánh tìm đất, và đang hướng xuống, quẹt đường thẳng 5 block dưới chân
        if (session.isSearchingLand && session.target.y <= mob.y) {
            var isWaterBelow = false
            var foundGround = false
            val level = mob.level()
            
            for (i in 1..5) {
                val checkY = mob.y.toInt() - i
                val pos = net.minecraft.core.BlockPos(mob.x.toInt(), checkY, mob.z.toInt())
                val state = level.getBlockState(pos)
                if (!state.isAir) {
                    foundGround = true
                    if (!state.fluidState.isEmpty) {
                        isWaterBelow = true
                    }
                    break
                }
            }

            if (foundGround && isWaterBelow) {
                // Đã tìm thấy mặt nước dưới chân -> Hủy hạ cánh, báo hiệu cần nảy cho AI
                session.needsBounce = true
                return
            }
        }

        val p = session.config
        val target = session.target
        val dx = target.x - mob.x
        val dy = target.y - mob.y
        val dz = target.z - mob.z

        FlightHelpers.applyFlyingPhysics(session.pokemon)

        val horizDist = kotlin.math.sqrt(dx * dx + dz * dz)
        val vertDist = kotlin.math.abs(dy)

        if (horizDist < p.arriveThresholdHoriz && vertDist < p.arriveThresholdVert) {
            if (session.isSearchingLand) {
                // Đã bay đến điểm tìm kiếm nhưng vẫn chưa thấy đất, tiếp tục tìm kiếm
                land(session.pokemon, p, true)
                return
            }

            if (session.hover) {
                mob.deltaMovement = Vec3.ZERO
                session.state = InternalFlightState.ARRIVED_HOVER
            } else {
                mob.isNoGravity = false
                session.state = InternalFlightState.FALLING
            }
            return
        }

        var rawVec = Vec3(dx, dy, dz)
        if (p.obstacleAvoidance) {
            rawVec = FlightHelpers.checkObstacleAhead(mob, rawVec, p.obstacleCheckRange, p.obstacleClimbAmount)
        }

        val speed = resolveSpeed(mob, session)
        val targetVelocity = rawVec.normalize().scale(speed)
        mob.deltaMovement = mob.deltaMovement.lerp(targetVelocity, p.lerpFactor)
        mob.hasImpulse = true
        mob.move(MoverType.SELF, mob.deltaMovement)
        FlightHelpers.syncRotationFromVelocity(mob)
    }

    // Lơ lửng tại đích: cố định cứng tại mục tiêu, không sway để tránh cảm giác bị kẹt
    private fun tickHovering(mob: net.minecraft.world.entity.Mob, session: FlightSession) {
        FlightHelpers.applyFlyingPhysics(session.pokemon)
        
        val dxSnap = (session.target.x - mob.x) * 0.1
        val dySnap = (session.target.y - mob.y) * 0.1
        val dzSnap = (session.target.z - mob.z) * 0.1
        
        val targetVelocity = Vec3(dxSnap, dySnap, dzSnap)
        mob.deltaMovement = mob.deltaMovement.lerp(targetVelocity, 0.5)
        mob.hasImpulse = true
        mob.move(MoverType.SELF, mob.deltaMovement)
    }

    // Rơi tự do: đợi chạm đất hoặc chạm nước thì kết thúc session
    private fun tickFalling(mob: net.minecraft.world.entity.Mob, session: FlightSession) {
        val level = mob.level()
        val posBelow = mob.blockPosition().below()
        val stateBelow = level.getBlockState(posBelow)
        
        val isPhysicallyOnGround = mob.onGround() || 
            (!stateBelow.isAir && !stateBelow.getCollisionShape(level, posBelow).isEmpty)

        if (isPhysicallyOnGround || mob.isInWater || mob.isUnderWater) {
            FlightHelpers.terminateFlight(session.pokemon)
            mob.deltaMovement = Vec3.ZERO
            session.state = InternalFlightState.DONE
        }
    }

    private fun terminateSession(session: FlightSession) {
        FlightHelpers.terminateFlight(session.pokemon)
        session.state = InternalFlightState.DONE
    }

    // Tính tốc độ thực tế, có scale theo khoảng cách player nếu bật
    private fun resolveSpeed(mob: net.minecraft.world.entity.Mob, session: FlightSession): Double {
        var speed = session.config.flightSpeed
        if (session.config.speedPlayerScale) {
            val dist = FlightHelpers.nearestPlayerDistance(mob)
            // Nếu dist < 0 (không có ai trong 128 block), coi như xa tối đa -> ratio = 1.0
            val ratio = if (dist < 0.0) 1.0 else (dist / 128.0).coerceIn(0.0, 1.0)
            // Giảm tối đa 70% tốc độ (nhân với 0.3) khi ở xa
            speed *= (1.0 - ratio * 0.7)
        }
        return speed
    }
}
