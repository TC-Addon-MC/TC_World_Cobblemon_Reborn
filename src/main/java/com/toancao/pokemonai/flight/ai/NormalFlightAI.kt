package com.toancao.pokemonai.flight.ai

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.flight.CustomFlightProfile
import com.toancao.pokemonai.flight.FlightHelpers
import com.toancao.pokemonai.flight.engine.FlightEngine
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

// Tầng ra quyết định thuần: không đụng vào deltaMovement, move(), hay obstacle trực tiếp — mọi di chuyển qua FlightEngine
object NormalFlightAI {

    fun tickGrounded(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        FlightHelpers.restoreAI(pokemon as net.minecraft.world.entity.Mob)
    }

    private fun easeInOutQuad(t: Double) = if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t

    // Cất cánh: sử dụng cơ chế Damping + Easing + Particle Effects
    fun tickTakingOff(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int): Boolean {
        val mob = pokemon as net.minecraft.world.entity.Mob
        val config = profile.config
        val takeoffDuration = config.takeoffDuration
        
        if (profile.ticksInCurrentState == 1) {
            // Thêm chút lực nảy nhẹ ban đầu
            mob.deltaMovement = mob.deltaMovement.add(0.0, 0.2, 0.0)
        }
        
        val progress = (profile.ticksInCurrentState.toDouble() / takeoffDuration).coerceIn(0.0, 1.0)
        val easedProgress = easeInOutQuad(progress) * config.takeoffAcceleration
        
        val radians = Math.toRadians(profile.currentYaw)
        val dirX = -sin(radians)
        val dirZ = cos(radians)
        val groundY = FlightHelpers.estimateGroundY(mob)
        val targetY = groundY + profile.currentPreferredHeight
        
        // Tạo tốc độ tăng dần theo easedProgress (min 10% speed)
        val currentSpeed = config.flightSpeed * (0.1 + easedProgress * 0.9)
        
        // Gọi FlightEngine.flyTo để tạo session và quản lý vật lý, lerp
        FlightEngine.flyTo(
            pokemon = pokemon,
            target = Vec3(mob.x + dirX * 5.0, targetY, mob.z + dirZ * 5.0),
            hover = false,
            config = profile.config.copy(flightSpeed = currentSpeed)
        )
        
        FlightHelpers.spawnTakeoffParticles(mob.level(), mob, progress, profile.takeoffParticleStyle)
        
        return progress >= 0.95 || (mob.y - groundY) >= profile.currentPreferredHeight * 0.8
    }

    // Bay tự nhiên: cập nhật hướng theo interval, gọi flyTo mỗi tick với target ảo phía trước
    fun tickFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob

        if (tick % profile.config.directionChangeInterval == 0) {
            FlightHelpers.applyDirectionChange(profile)
        }

        val radians = Math.toRadians(profile.currentYaw)
        val dirX = -sin(radians)
        val dirZ = cos(radians)
        val groundY = FlightHelpers.estimateGroundY(mob)
        val sinSway = sin(tick * 0.02 + mob.id * 0.3) * profile.config.verticalSway
        
        // Dự phòng: check 2 block dưới chân, nếu có block thì bay cao thêm 5 block
        var extraHeight = 0.0
        val level = mob.level()
        for (i in 1..2) {
            val checkPos = net.minecraft.core.BlockPos(mob.x.toInt(), mob.y.toInt() - i, mob.z.toInt())
            if (!level.getBlockState(checkPos).isAir) {
                extraHeight = 5.0
                break
            }
        }
        
        val targetY = groundY + profile.currentPreferredHeight + sinSway + extraHeight

        FlightEngine.flyTo(
            pokemon = pokemon,
            target = Vec3(mob.x + dirX * 10.0, targetY, mob.z + dirZ * 10.0),
            hover = false,
            config = profile.config
        )
    }

    fun tickWaterHovering(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        
        // Cập nhật vị trí mỗi 100 tick (5s) hoặc khi mới bắt đầu
        if (profile.ticksInCurrentState == 1 || profile.ticksInCurrentState % 100 == 0) {
            val surfaceY = FlightHelpers.estimateWaterSurfaceY(mob)
            val targetY = surfaceY + 2.0 // Lơ lửng cách mặt nước 2 block

            // Tỉ lệ 50% sẽ đổi vị trí lơ lửng đi một đoạn ngắn xung quanh (trên/dưới/ngang)
            if (profile.ticksInCurrentState == 1 || kotlin.random.Random.nextDouble() < 0.5) {
                val radians = kotlin.random.Random.nextDouble() * 2.0 * Math.PI
                val dist = kotlin.random.Random.nextDouble(2.0, 5.0)
                val dirX = kotlin.math.cos(radians) * dist
                val dirZ = kotlin.math.sin(radians) * dist
                
                // Độ dời Y ngẫu nhiên từ -1.5 đến +2.5 block (nhưng không được phép lặn xuống dưới mặt nước)
                val dirY = kotlin.random.Random.nextDouble(-1.5, 2.5)
                val finalTargetY = kotlin.math.max(surfaceY + 1.0, targetY + dirY)
                
                FlightEngine.flyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x + dirX, finalTargetY, mob.z + dirZ),
                    hover = true,
                    config = profile.config
                )
            } else {
                // Đứng im tại chỗ
                FlightEngine.flyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x, targetY, mob.z),
                    hover = true,
                    config = profile.config
                )
            }
        }
    }
    fun tickGroundHovering(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        
        // Cập nhật vị trí mỗi 100 tick (5s) hoặc khi mới bắt đầu
        if (profile.ticksInCurrentState == 1 || profile.ticksInCurrentState % 100 == 0) {
            val groundY = FlightHelpers.estimateGroundY(mob)
            val targetY = groundY + 3.5 // Lơ lửng cách mặt đất 3.5 block

            // Tỉ lệ 70% sẽ đổi vị trí lơ lửng đi một đoạn ngắn xung quanh
            if (profile.ticksInCurrentState == 1 || kotlin.random.Random.nextDouble() < 0.7) {
                val radians = kotlin.random.Random.nextDouble() * 2.0 * Math.PI
                val dist = kotlin.random.Random.nextDouble(2.0, 5.0)
                val dirX = kotlin.math.cos(radians) * dist
                val dirZ = kotlin.math.sin(radians) * dist
                
                // Độ dời Y ngẫu nhiên từ -0.5 đến +1.5 block
                val dirY = kotlin.random.Random.nextDouble(-0.5, 1.5)
                val finalTargetY = kotlin.math.max(groundY + 2.0, targetY + dirY)
                
                FlightEngine.flyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x + dirX, finalTargetY, mob.z + dirZ),
                    hover = true,
                    config = profile.config
                )
            } else {
                // Đứng im tại chỗ
                FlightEngine.flyTo(
                    pokemon = pokemon,
                    target = net.minecraft.world.phys.Vec3(mob.x, targetY, mob.z),
                    hover = true,
                    config = profile.config
                )
            }
        }
    }


    fun tickCircularFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int) {
        val mob = pokemon as net.minecraft.world.entity.Mob
        val radius = profile.config.circularFlightRadius
        val speed = profile.config.flightSpeed // Vận tốc thực tế mà FlightEngine đang dùng

        if (profile.ticksInCurrentState <= 1 || profile.circularFlightCenter == null) {
            val groundY = FlightHelpers.estimateGroundY(mob)
            // Lấy độ cao cao nhất giữa mặt đất tại tâm và anchor
            val targetY = Math.max(profile.anchorY, groundY + profile.currentPreferredHeight)
            
            // Chốt điểm spawn (anchor) làm tâm xoay
            profile.circularFlightCenter = Vec3(profile.anchorX, targetY, profile.anchorZ)
            // Tính góc xuất phát
            profile.circularFlightAngle = Math.atan2(mob.z - profile.circularFlightCenter!!.z, mob.x - profile.circularFlightCenter!!.x)
        }

        val center = profile.circularFlightCenter!!
        
        // Tính góc hiện tại của Pokemon so với tâm
        val currentAngle = Math.atan2(mob.z - center.z, mob.x - center.x)
        
        // Loại bỏ việc phóng đại tốc độ (*10.0 cũ) để góc nhìn bám sát thực tế hơn.
        // Góc lookahead tối thiểu là 0.3 rad (~17 độ) để đảm bảo khoảng cách mục tiêu > 2.0 block (tránh arriveThreshold)
        // nhưng vẫn đủ nhỏ để vector vận tốc gần như tiếp tuyến, giúp đầu Pokemon hướng về phía trước tự nhiên.
        val angularSpeed = speed / radius
        val targetAngle = currentAngle + Math.max(angularSpeed * 1.5, 0.3)
        
        val targetX = center.x + radius * cos(targetAngle)
        val targetZ = center.z + radius * sin(targetAngle)
        
        // Sway độ cao nhẹ để lượn cho tự nhiên (chỉnh chậm lại cho giống bay thẳng)
        val sinSway = sin(tick * 0.02 + mob.id * 0.3) * profile.config.verticalSway
        
        // Dự phòng: check 2 block dưới chân, nếu có block thì bay cao thêm 5 block
        var extraHeight = 0.0
        val level = mob.level()
        for (i in 1..2) {
            val checkPos = net.minecraft.core.BlockPos(mob.x.toInt(), mob.y.toInt() - i, mob.z.toInt())
            if (!level.getBlockState(checkPos).isAir) {
                extraHeight = 5.0
                break
            }
        }
        
        val targetY = center.y + sinSway + extraHeight

        FlightEngine.flyTo(
            pokemon = pokemon,
            target = Vec3(targetX, targetY, targetZ),
            hover = false,
            config = profile.config
        )
    }

    enum class LandingResult { CONTINUE, DONE, BOUNCE }

    // Hạ cánh: Engine tự tính đường xuống mượt mà và tự kết thúc khi chạm đất
    fun tickLanding(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int): LandingResult {
        val mob = pokemon as net.minecraft.world.entity.Mob
        
        // Bắt đầu gọi lệnh hạ cánh ở tick đầu tiên
        if (profile.ticksInCurrentState == 1) {
            FlightEngine.land(pokemon, profile.config, avoidWater = profile.bounceCount < 3)
            return LandingResult.CONTINUE
        }

        if (FlightEngine.needsBounce(pokemon)) {
            FlightEngine.stopFlight(pokemon)
            return LandingResult.BOUNCE
        }

        // Nếu Engine đã tự kết thúc session (chạm đất/nước), hoặc tự check thấy chạm đất -> Hoàn thành hạ cánh
        if (!FlightEngine.hasActiveFlight(pokemon) || mob.onGround() || mob.isInWater || mob.isUnderWater) {
            FlightEngine.stopFlight(pokemon) // Đảm bảo dọn dẹp
            return LandingResult.DONE
        }

        return LandingResult.CONTINUE
    }
}
