package com.toancao.flyingspawn;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Random;

/**
 * Điều khiển trạng thái bay của từng Pokemon riêng biệt.
 */
public class FlightStateMachine {

    private static final Logger LOGGER = LogManager.getLogger("FlyingSpawn");
    private static final Random RNG = new Random();

    private final PokemonEntity pokemon;
    private final PokemonFlightProfile profile;
    private FlightState state;
    private int globalTick = 0;

    public FlightStateMachine(PokemonEntity pokemon, FlightState initialState) {
        this.pokemon = pokemon;
        this.profile = new PokemonFlightProfile(pokemon);
        this.state = initialState;

        if (initialState == FlightState.FLYING) {
            applyInitialFlight();
        }
    }

    /** Chạy logic mỗi tick tùy theo state hiện tại */
    public void tick() {
        globalTick++;
        profile.ticksInCurrentState++;

        switch (state) {
            case GROUNDED -> {
                pokemon.setFlying(false);
                tickGrounded();
            }
            case TAKING_OFF -> {
                pokemon.setFlying(true);
                tickTakingOff();
            }
            case FLYING -> {
                pokemon.setFlying(true);
                tickFlying();
            }
            case LANDING -> {
                pokemon.setFlying(true);
                tickLanding();
            }
        }
    }

    private void tickGrounded() {
        GroundedBehavior.tick(pokemon, profile, globalTick);
        FlyingSpawnConfig cfg = FlyingSpawnConfig.get();

        if (globalTick % cfg.transitionCheckInterval == 0) {
            boolean nearPlayer = isPlayerNearby(cfg);
            boolean openSpace = isOpenSpace(cfg);
            double takeoffChance = profile.computedTakeoffChance(nearPlayer, openSpace);

            if (RNG.nextDouble() < takeoffChance) {
                transitionTo(FlightState.TAKING_OFF);
            }
        }
    }

    private void tickTakingOff() {
        boolean done = FlyingBehavior.tickTakingOff(pokemon, profile, globalTick);
        if (done) transitionTo(FlightState.FLYING);
    }

    private void tickFlying() {
        FlyingBehavior.tick(pokemon, profile, globalTick);
        FlyingSpawnConfig cfg = FlyingSpawnConfig.get();

        if (globalTick % cfg.transitionCheckInterval == 0) {
            boolean nearGround = isNearGround(cfg);
            boolean flyingLong = profile.ticksInCurrentState > cfg.flyingLongThreshold;
            double landingChance = profile.computedLandingChance(nearGround, flyingLong);

            if (RNG.nextDouble() < landingChance) {
                profile.verticalVelocity = 0.0;
                transitionTo(FlightState.LANDING);
            }
        }
    }

    private void tickLanding() {
        boolean done = FlyingBehavior.tickLanding(pokemon, profile, globalTick);
        if (done) transitionTo(FlightState.GROUNDED);
    }

    private void transitionTo(FlightState newState) {
        state = newState;
        profile.ticksInCurrentState = 0;

        if (newState == FlightState.TAKING_OFF) {
            profile.refreshPreferredHeight();
            profile.verticalVelocity = 0.0;
            profile.currentYaw = RNG.nextDouble() * 360.0;
        }
    }

    private boolean isPlayerNearby(FlyingSpawnConfig cfg) {
        if (pokemon.level() == null) return false;
        double r = cfg.playerAlertRadius;
        List<Player> players = pokemon.level().getEntitiesOfClass(
                Player.class,
                new AABB(pokemon.getX() - r, pokemon.getY() - r, pokemon.getZ() - r,
                        pokemon.getX() + r, pokemon.getY() + r, pokemon.getZ() + r)
        );
        return !players.isEmpty();
    }

    private boolean isOpenSpace(FlyingSpawnConfig cfg) {
        if (pokemon.level() == null) return false;
        int airCount = 0;
        int px = (int) pokemon.getX(), py = (int) pokemon.getY(), pz = (int) pokemon.getZ();

        for (int dx = -3; dx <= 3; dx += 2) {
            for (int dy = 0; dy <= 6; dy += 2) {
                for (int dz = -3; dz <= 3; dz += 2) {
                    var pos = new net.minecraft.core.BlockPos(px + dx, py + dy, pz + dz);
                    if (pokemon.level().getBlockState(pos).isAir()) airCount++;
                }
            }
        }
        return airCount >= cfg.openSpaceThreshold;
    }

    private boolean isNearGround(FlyingSpawnConfig cfg) {
        return pokemon.onGround() || (pokemon.level() != null && isBlockBelow(cfg.nearGroundBlockDist));
    }

    private boolean isBlockBelow(int maxDist) {
        for (int i = 1; i <= maxDist; i++) {
            var pos = new net.minecraft.core.BlockPos((int) pokemon.getX(), (int) (pokemon.getY() - i), (int) pokemon.getZ());
            if (pokemon.level() != null && !pokemon.level().getBlockState(pos).isAir()) return true;
        }
        return false;
    }

    /** Tránh kẹt vào lá cây khi vừa sinh ra */
    private void applyInitialFlight() {
        pokemon.setNoGravity(true);
        FlyingSpawnConfig cfg = FlyingSpawnConfig.get();
        double startX = pokemon.getX(), startY = pokemon.getY(), startZ = pokemon.getZ();
        double highestSolidY = startY;

        if (pokemon.level() != null) {
            for (int dy = 1; dy <= cfg.initialFlightScanUp; dy++) {
                var pos = new net.minecraft.core.BlockPos((int)startX, (int)(startY + dy), (int)startZ);
                if (pokemon.level().getBlockState(pos).isSolid()) highestSolidY = startY + dy;
            }
        }

        double finalY = highestSolidY + profile.preferredHeight;

        pokemon.setDeltaMovement(0, 0, 0);
        // Dùng teleport chuẩn thay vì nhấc lên quá cao để không bị xóa khỏi thế giới
        pokemon.teleportTo(startX, finalY, startZ);
        pokemon.setOldPosAndRot();
        pokemon.hurtMarked = true; // Ép client cập nhật vị trí ngay lập tức
    }

    public FlightState getState() { return state; }

    public boolean isAlive() {
        return pokemon.isAlive() && !pokemon.isRemoved() && pokemon.level() != null;
    }

    public void deactivate() {
        pokemon.setNoGravity(false);
        pokemon.setFlying(false);
        pokemon.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
}