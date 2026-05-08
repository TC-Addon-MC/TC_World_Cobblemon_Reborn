package com.toancao.battlegrowth;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class BattleGrowthMod implements ModInitializer {

    // Kiểm tra mỗi 200 tick (~10 giây) thay vì mỗi tick để tránh lag
    private static final int CHECK_INTERVAL_TICKS = 200;
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        // Tạo/kiểm tra file config ngay khi Fabric load mod
        BattleGrowthConfig.load();

        // Sync với Cobblemon sau khi server sẵn sàng
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BattleGrowthConfig.get().apply();
        });

        // Monitor Cobblemon experienceMultiplier mỗi 10 giây
        // Nếu admin bật lại EXP Cobblemon → tự động tắt faint bonus của mod
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < CHECK_INTERVAL_TICKS) return;
            tickCounter = 0;

            try {
                float multiplier = com.cobblemon.mod.common.Cobblemon.INSTANCE
                        .getConfig().getExperienceMultiplier();

                boolean faintBonusCurrently = ExpConfig.isCustomExpEnabledFaintBonus();

                if (multiplier > 0 && faintBonusCurrently) {
                    // Admin bật EXP Cobblemon → tắt faint bonus để tránh cộng 2 lần
                    ExpConfig.setCustomExpEnabledFaintBonus(false);
                    System.out.println("[BattleGrowth] Phát hiện Cobblemon EXP đang bật (x"
                            + multiplier + ") → tự động TẮT faint bonus EXP của mod.");

                } else if (multiplier <= 0 && !faintBonusCurrently
                        && BattleGrowthConfig.get().customExpEnabledFaintBonus) {
                    // Admin tắt lại EXP Cobblemon + config mod muốn bật → bật lại faint bonus
                    ExpConfig.setCustomExpEnabledFaintBonus(true);
                    System.out.println("[BattleGrowth] Cobblemon EXP đã tắt → bật lại faint bonus EXP của mod.");
                }

            } catch (Exception e) {
                // Bỏ qua nếu Cobblemon chưa sẵn sàng
            }
        });
    }
}