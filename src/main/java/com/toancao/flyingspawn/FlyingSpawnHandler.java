package com.toancao.flyingspawn;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

/** Lắng nghe entity spawn và set flight state ban đầu. */
public class FlyingSpawnHandler {

    private static final Logger LOGGER = LogManager.getLogger("FlyingSpawn");
    private static final Random RNG = new Random();

    public static void register() {
        LOGGER.info("FlyingSpawnHandler: Đã đăng ký event ENTITY_LOAD");

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof PokemonEntity pokemon)) return;
            if (!FlyingCapabilityChecker.canFly(pokemon)) return;

            world.getServer().execute(() -> {
                if (!FlightContext.isEligible(pokemon)) return;
                FlightState initialState = decideInitialState(pokemon);
                FlightTickManager.register(pokemon, initialState);
            });
        });
    }

    private static FlightState decideInitialState(PokemonEntity pokemon) {
        FlyingSpawnConfig cfg = FlyingSpawnConfig.get();
        double typeMultiplier = getTypeGroundedMultiplier(pokemon, cfg);

        if (isSpawnedOnTree(pokemon, cfg)) {
            double treeChance = cfg.treeGroundedChance * typeMultiplier;
            return RNG.nextDouble() < Math.min(treeChance, 1.0) ? FlightState.GROUNDED : FlightState.FLYING;
        }

        boolean openArea = isOpenSpawnArea(pokemon, cfg);
        double groundedChance = openArea ? cfg.openGroundedChance : cfg.baseGroundedChance;
        groundedChance *= typeMultiplier;

        return RNG.nextDouble() < Math.min(groundedChance, 1.0) ? FlightState.GROUNDED : FlightState.FLYING;
    }

    private static double getTypeGroundedMultiplier(PokemonEntity pokemon, FlyingSpawnConfig cfg) {
        var types = pokemon.getPokemon().getTypes();
        double multiplier = 1.0;

        for (var type : types) {
            String name = type.getName().toLowerCase();
            multiplier *= cfg.typeGrounded.getOrDefault(name, 1.0);
        }
        return Math.max(cfg.groundMultMin, Math.min(cfg.groundMultMax, multiplier));
    }

    private static boolean isSpawnedOnTree(PokemonEntity pokemon, FlyingSpawnConfig cfg) {
        if (pokemon.level() == null) return false;
        for (int dy = -1; dy >= -cfg.treeSpawnScanDepth; dy--) {
            var pos = new net.minecraft.core.BlockPos((int) pokemon.getX(), (int) (pokemon.getY() + dy), (int) pokemon.getZ());
            var state = pokemon.level().getBlockState(pos);
            if (state.isAir()) continue;
            String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (blockName.contains("log") || blockName.contains("leaves") || blockName.contains("wood")) {
                return true;
            }
            break;
        }
        return false;
    }

    private static boolean isOpenSpawnArea(PokemonEntity pokemon, FlyingSpawnConfig cfg) {
        if (pokemon.level() == null) return false;

        int solidCount = 0;
        int px = (int) pokemon.getX(), py = (int) pokemon.getY(), pz = (int) pokemon.getZ();
        int r = cfg.spawnAreaRadius;
        int h = cfg.spawnAreaHeight;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 1; dy <= h; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    var pos = new net.minecraft.core.BlockPos(px + dx, py + dy, pz + dz);
                    if (pokemon.level().getBlockState(pos).isSolid()) solidCount++;
                }
            }
        }
        return solidCount < cfg.spawnSolidCountMax;
    }
}