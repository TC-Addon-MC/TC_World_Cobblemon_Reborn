package com.toancao.pokemonai.events;

import java.util.HashSet;
import java.util.Set;

import com.toancao.pokemonai.blocks.entity.DragonGateBottomBlockEntity;
import com.toancao.pokemonai.compat.CobblemonBridge;
import com.toancao.pokemonai.utils.DebugUtils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DragonGateEvent {
    public enum EventPhase {
        IDLE,
        SWIMMING,
        EVOLVING
    }

    public static EventPhase currentPhase = EventPhase.IDLE;
    public static int phaseTicks = 0;
    
    public static final Set<BlockPos> topBlocks = new HashSet<>();
    public static final Set<BlockPos> bottomBlocks = new HashSet<>();

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            tick(level);
        });
    }

    public static void tick(ServerLevel level) {
        if (!level.dimension().location().toString().equals("minecraft:overworld")) return;

        long dayTime = level.getDayTime();
        long currentDay = dayTime / 24000;
        long timeOfDay = dayTime % 24000;
        
        int interval = com.toancao.pokemonai.config.MagikarpConfigManager.INSTANCE.getConfig().getEventIntervalMultiplier() * 10;
        
        if (currentPhase == EventPhase.IDLE && currentDay > 0 && currentDay % interval == 0 && timeOfDay == 1000) {
            trigger(level);
        }

        if (currentPhase != EventPhase.IDLE) {
            phaseTicks--;

            if (currentPhase == EventPhase.SWIMMING) {
                if (phaseTicks <= 0 || timeOfDay >= 18000 && timeOfDay < 18100) {
                    currentPhase = EventPhase.EVOLVING;
                    phaseTicks = com.toancao.pokemonai.config.MagikarpConfigManager.INSTANCE.getConfig().getEvolvingPhaseDurationTicks();
                    broadcast(level, "[Sự Kiện Long Môn] Trăng đã lên đỉnh! Bắt đầu nghi thức tiến hóa cho các cá chép trên đỉnh thác!");
                }
            } else if (currentPhase == EventPhase.EVOLVING) {
                if (phaseTicks <= 0) {
                    currentPhase = EventPhase.IDLE;
                    phaseTicks = 0;
                    broadcast(level, "[Sự Kiện Long Môn] Sự kiện kết thúc. Hẹn gặp lại kỳ sau!");
                    
                    clearEventTags(level);
                    com.toancao.pokemonai.api.PokemonAIEvents.DRAGON_GATE_END.invoker().onDragonGateEnd(level);
                }
            }
        }
    }

    public static void trigger(ServerLevel level) {
        if (!com.toancao.pokemonai.api.PokemonAIEvents.DRAGON_GATE_START.invoker().onDragonGateStart(level)) {
            return;
        }

        currentPhase = EventPhase.SWIMMING;
        phaseTicks = com.toancao.pokemonai.config.MagikarpConfigManager.INSTANCE.getConfig().getSwimmingPhaseDurationTicks();
        
        DebugUtils.INSTANCE.logEvent("DragonGateEvent started! Scanning existing Magikarps.");
        broadcast(level, "[Sự Kiện Long Môn] Bắt đầu! Đàn cá hoang dã đang cảm nhận tiếng gọi và bơi về chân thác.");
        
        for (BlockPos topPos : topBlocks) {
            BlockPos b1 = topPos.offset(32, -18, 3);
            if (!(level.getBlockState(b1).getBlock() instanceof com.toancao.pokemonai.blocks.DragonGateBottomBlock)) {
                level.setBlock(b1, com.toancao.pokemonai.registry.BlockRegistry.INSTANCE.getDRAGON_GATE_BOTTOM_BLOCK().defaultBlockState(), 3);
            }
            BlockEntity be1 = level.getBlockEntity(b1);
            if (be1 instanceof DragonGateBottomBlockEntity) {
                ((DragonGateBottomBlockEntity) be1).buildWaypointChain(level, topPos);
            }

            BlockPos b2 = topPos.offset(-31, -18, 10);
            if (!(level.getBlockState(b2).getBlock() instanceof com.toancao.pokemonai.blocks.DragonGateBottomBlock)) {
                level.setBlock(b2, com.toancao.pokemonai.registry.BlockRegistry.INSTANCE.getDRAGON_GATE_BOTTOM_BLOCK().defaultBlockState(), 3);
            }
            BlockEntity be2 = level.getBlockEntity(b2);
            if (be2 instanceof DragonGateBottomBlockEntity) {
                ((DragonGateBottomBlockEntity) be2).buildWaypointChain(level, topPos);
            }
        }

        if (bottomBlocks.isEmpty()) return;

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity && CobblemonBridge.checkIsPokemonEntity(entity)) {
                String pokemonName = CobblemonBridge.getSpeciesName(CobblemonBridge.castToPokemonEntity(entity));
                if ("magikarp".equals(pokemonName)) {
                    if (CobblemonBridge.isWild(CobblemonBridge.castToPokemonEntity(entity))) {
                        BlockPos nearest = null;
                        double minDist = Double.MAX_VALUE;
                        for (BlockPos b : bottomBlocks) {
                            double d = b.distSqr(entity.blockPosition());
                            if (d < minDist) {
                                minDist = d;
                                nearest = b;
                            }
                        }
                        
                        if (nearest != null) {
                            entity.addTag("dragon_gate_challenger");
                            entity.addTag("start_x_" + entity.getBlockX());
                            entity.addTag("start_y_" + entity.getBlockY());
                            entity.addTag("start_z_" + entity.getBlockZ());
                            entity.addTag("target_x_" + nearest.getX());
                            entity.addTag("target_y_" + nearest.getY());
                            entity.addTag("target_z_" + nearest.getZ());
                        }
                    }
                }
            }
        }
    }

    public static void stop(ServerLevel level) {
        currentPhase = EventPhase.IDLE;
        phaseTicks = 0;
        clearEventTags(level);
        com.toancao.pokemonai.api.PokemonAIEvents.DRAGON_GATE_END.invoker().onDragonGateEnd(level);
        DebugUtils.INSTANCE.logEvent("DragonGateEvent force stopped!");
    }

    public static void clearEventTags(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity) {
                java.util.List<String> toRemove = new java.util.ArrayList<>();
                for (String tag : entity.getTags()) {
                    if (tag.startsWith("start_") || tag.startsWith("target_") || 
                        tag.equals("gyarados_resting") || tag.equals("dragon_gate_challenger") || 
                        tag.equals("waiting_for_evolution") || tag.equals("evolution_eligible") || 
                        tag.equals("dragon_gate_free_swim")) {
                        toRemove.add(tag);
                    }
                }
                for (String tag : toRemove) {
                    entity.removeTag(tag);
                }
            }
        }
    }

    private static void broadcast(ServerLevel level, String msg) {
        if (DebugUtils.INSTANCE.getEnabled()) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(msg), false
            );
        }
    }
}