package com.toancao.pokemonai.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import com.toancao.pokemonai.compat.CobblemonBridge;
import com.toancao.pokemonai.utils.DebugUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.toancao.pokemonai.blocks.entity.DragonGateBottomBlockEntity;

import java.util.HashSet;
import java.util.Set;

public class DragonGateEvent {
    public enum EventPhase {
        IDLE,
        SWIMMING,  // 7h morning -> Midnight (1000 -> 18000 ticks)
        EVOLVING,  // 30 seconds (600 ticks)
        FREE_SWIM, // 1 minute (1200 ticks)
        JUMPING    // Short trigger phase
    }

    public static EventPhase currentPhase = EventPhase.IDLE;
    public static int phaseTicks = 0;
    
    // Registry for Top blocks
    public static final Set<BlockPos> topBlocks = new HashSet<>();
    // Registry for Bottom blocks to find the nearest
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
        
        // Kích hoạt lúc 7h sáng các ngày 30, 60, 90...
        if (currentPhase == EventPhase.IDLE && currentDay > 0 && currentDay % 30 == 0 && timeOfDay == 1000) {
            trigger(level);
        }

        if (currentPhase != EventPhase.IDLE) {
            phaseTicks--;

            if (currentPhase == EventPhase.SWIMMING) {
                // Trăng lên đỉnh lúc nửa đêm (18000 ticks) - 17000 ticks after 1000
                if (phaseTicks <= 0 || timeOfDay >= 18000 && timeOfDay < 18100) {
                    currentPhase = EventPhase.EVOLVING;
                    phaseTicks = 600; // 30s
                    broadcast(level, "[Sự Kiện Long Môn] Trăng đã lên đỉnh! Bắt đầu nghi thức tiến hóa cho các cá chép trên đỉnh thác!");
                }
            } else if (currentPhase == EventPhase.EVOLVING) {
                if (phaseTicks <= 0) {
                    currentPhase = EventPhase.FREE_SWIM;
                    phaseTicks = 1200; // 1 min
                    broadcast(level, "[Sự Kiện Long Môn] Nghi thức tiến hóa hoàn tất! Các rồng đang bơi lượn tự do.");
                }
            } else if (currentPhase == EventPhase.FREE_SWIM) {
                if (phaseTicks <= 0) {
                    currentPhase = EventPhase.JUMPING;
                    phaseTicks = 100;
                    broadcast(level, "[Sự Kiện Long Môn] Các Gyarados chuẩn bị phóng khỏi mặt nước để trở về!");
                }
            } else if (currentPhase == EventPhase.JUMPING) {
                if (phaseTicks <= 0) {
                    currentPhase = EventPhase.IDLE;
                    phaseTicks = 0;
                    broadcast(level, "[Sự Kiện Long Môn] Sự kiện kết thúc. Hẹn gặp lại kỳ sau!");
                    
                    // Xoá toàn bộ tag cho mọi thực thể để an toàn
                    for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof LivingEntity) {
                            java.util.List<String> toRemove = new java.util.ArrayList<>();
                            for (String tag : entity.getTags()) {
                                if (tag.startsWith("start_") || tag.startsWith("target_") || tag.equals("gyarados_resting") || tag.equals("dragon_gate_challenger") || tag.equals("waiting_for_evolution")) {
                                    toRemove.add(tag);
                                }
                            }
                            for (String tag : toRemove) {
                                entity.removeTag(tag);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void trigger(ServerLevel level) {
        currentPhase = EventPhase.SWIMMING;
        phaseTicks = 17000; // Từ 7h sáng tới 24h đêm
        
        DebugUtils.INSTANCE.logEvent("DragonGateEvent started! Scanning existing Magikarps.");
        broadcast(level, "[Sự Kiện Long Môn] Bắt đầu! Đàn cá hoang dã đang cảm nhận tiếng gọi và bơi về chân thác.");
        
        // Kiểm tra và khôi phục Bottom Blocks từ Top Blocks
        for (BlockPos topPos : topBlocks) {
            // Bottom 1 relative offset: (32, -18, 3)
            BlockPos b1 = topPos.offset(32, -18, 3);
            if (!(level.getBlockState(b1).getBlock() instanceof com.toancao.pokemonai.blocks.DragonGateBottomBlock)) {
                level.setBlock(b1, com.toancao.pokemonai.registry.BlockRegistry.INSTANCE.getDRAGON_GATE_BOTTOM_BLOCK().defaultBlockState(), 3);
            }
            BlockEntity be1 = level.getBlockEntity(b1);
            if (be1 instanceof DragonGateBottomBlockEntity) {
                ((DragonGateBottomBlockEntity) be1).buildWaypointChain(level, topPos);
            }

            // Bottom 2 relative offset: (-31, -18, 10)
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

        // Quét nhẹ qua overworld tìm Magikarp hoang dã
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity && CobblemonBridge.checkIsPokemonEntity(entity)) {
                String pokemonName = CobblemonBridge.getSpeciesName(CobblemonBridge.castToPokemonEntity(entity));
                if ("magikarp".equals(pokemonName)) {
                    if (CobblemonBridge.isWild(CobblemonBridge.castToPokemonEntity(entity))) {
                        // Find nearest bottom
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
                            // Cấp quyền tham gia sự kiện
                            entity.addTag("dragon_gate_challenger");
                            // Khởi tạo tọa độ xuất phát là vị trí hiện tại (để lúc nhảy về)
                            entity.addTag("start_x_" + entity.getBlockX());
                            entity.addTag("start_y_" + entity.getBlockY());
                            entity.addTag("start_z_" + entity.getBlockZ());
                            // Trỏ hướng về bottom gần nhất
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
        DebugUtils.INSTANCE.logEvent("DragonGateEvent force stopped!");
    }

    private static void broadcast(ServerLevel level, String msg) {
        if (DebugUtils.INSTANCE.getEnabled()) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(msg), false
            );
        }
    }
}
