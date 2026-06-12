package com.toancao.pokemonai.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.toancao.pokemonai.utils.DebugUtils;
import com.toancao.pokemonai.events.DragonGateData;
import com.toancao.pokemonai.events.DragonGateManager;

import java.util.List;

public class DragonGateEvent {
    // 30 days = 30 * 24000 = 720000 ticks
    public static final long CYCLE_TICKS = 720000L;
    
    public static boolean isActive = false;
    public static int ticksRemaining = 0;
    
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            tick(level);
        });
    }

    public static void tick(ServerLevel level) {
        // Only run on the Overworld
        if (!level.dimension().location().toString().equals("minecraft:overworld")) return;

        long dayTime = level.getDayTime();
        
        // Kích hoạt sự kiện mỗi 30 ngày
        if (!isActive && dayTime > 0 && dayTime % CYCLE_TICKS == 0L) {
            trigger(level);
        }

        if (isActive) {
            ticksRemaining--;
            
            // Spawn cá ngẫu nhiên tại các cổng (giới hạn số lượng)
            if (ticksRemaining % 100 == 0) { // Mỗi 5 giây spawn 1 đợt
                spawnMagikarps(level);
            }
            
            if (ticksRemaining <= 0) {
                isActive = false;
                DebugUtils.INSTANCE.logEvent("DragonGateEvent ended.");
            }
        }
    }

    public static void trigger(ServerLevel level) {
        isActive = true;
        // Sự kiện kéo dài 1 ngày (24000 ticks)
        ticksRemaining = 24000;
        DebugUtils.INSTANCE.logEvent("DragonGateEvent started! Spawning Magikarps at gates.");
    }

    private static void spawnMagikarps(ServerLevel level) {
        List<DragonGateData> gates = DragonGateManager.INSTANCE.getGatesInDimension(level.dimension().location().toString());
        for (DragonGateData gate : gates) {
            BlockPos startPos = new BlockPos(gate.getStartX(), gate.getStartY(), gate.getStartZ());
            BlockPos endPos = new BlockPos(gate.getEndX(), gate.getEndY(), gate.getEndZ());

            // Spawn 1-3 con Magikarp Level 20+ tại Start
            int amount = 1 + level.random.nextInt(3);
            for (int i = 0; i < amount; i++) {
                // Sử dụng Cobblemon API để tạo Magikarp level >= 20
                PokemonProperties props = PokemonProperties.Companion.parse("magikarp level=20-30");
                PokemonEntity entity = (PokemonEntity) props.createEntity(level);
                
                if (entity == null) continue;

                // Spawn with a slight offset
                double offsetX = (level.random.nextDouble() - 0.5) * 4;
                double offsetZ = (level.random.nextDouble() - 0.5) * 4;
                entity.setPos(startPos.getX() + offsetX, startPos.getY(), startPos.getZ() + offsetZ);
                
                // Lưu target vào tag
                entity.addTag("dragon_gate_challenger");
                entity.addTag("target_x_" + endPos.getX());
                entity.addTag("target_y_" + endPos.getY());
                entity.addTag("target_z_" + endPos.getZ());
                
                level.addFreshEntity(entity);
            }
        }
    }
}
