package com.toancao.battlegrowth;

import com.cobblemon.mod.common.Cobblemon;

public class ExpControlCobblemon {

    public static void disableDefaultExp() {
        var config = Cobblemon.INSTANCE.getConfig();
        config.setExperienceMultiplier(0.0f);
        // Sau khi tắt mặc định, ta có thể bật custom (tùy nhu cầu bạn muốn bật tự động hay không)
        ExpConfig.refreshExperienceSettings();
    }

    public static void toggleDefaultExp() {
        var config = Cobblemon.INSTANCE.getConfig();
        float currentMultiplier = config.getExperienceMultiplier();

        if (currentMultiplier > 0) {
            config.setExperienceMultiplier(0.0f);
            System.out.println("BattleGrowth: TẮT mặc định Cobblemon.");
        } else {
            config.setExperienceMultiplier(1.0f);
            System.out.println("BattleGrowth: BẬT mặc định Cobblemon.");
        }

        // Luôn gọi refresh để đảm bảo customExpEnabledFaintBonus được cập nhật theo logic mới
        ExpConfig.refreshExperienceSettings();
    }
}