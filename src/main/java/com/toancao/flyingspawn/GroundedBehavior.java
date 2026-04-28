package com.toancao.flyingspawn;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Hành vi đi bộ ngẫu nhiên lúc chưa cất cánh. */
public class GroundedBehavior {

    private static final Random RNG = new Random();

    public static void tick(PokemonEntity pokemon, PokemonFlightProfile profile, int tick) {
        profile.idleTicks++;
    }

    private static void decideGroundAction(PokemonEntity pokemon, PokemonFlightProfile profile, FlyingSpawnConfig cfg) {
        if (RNG.nextDouble() < 0.5) {
            applyWalkMovement(pokemon, profile);
            profile.idleTicks = 0;
        }
    }

    private static void applyWalkMovement(PokemonEntity pokemon, PokemonFlightProfile profile) {
        // AI di chuyển
    }

    private static void applyLightHop(PokemonEntity pokemon, PokemonFlightProfile profile, FlyingSpawnConfig cfg) {
        Vec3 current = pokemon.getDeltaMovement();
        pokemon.setDeltaMovement(new Vec3(current.x, cfg.hopVelocityY, current.z));
    }
}