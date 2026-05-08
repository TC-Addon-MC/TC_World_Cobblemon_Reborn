package com.toancao.battlegrowth;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class BattleGrowthConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("battlegrowth.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ============================================================
    // Các giá trị config (mirror với ExpConfig)
    // ============================================================
    public double rateBaseHitMin        = 0.05;
    public double rateBaseHitMax        = 0.15;
    public double rateComboBonus        = 0.01;
    public double rateMiss              = 0.02;
    public double rateBuff              = 0.015;
    public double rateDebuff            = 0.01;
    public double rateMisc              = 0.005;

    public int    overBudgetExp         = 10;
    public double rateFaintBonusMin     = 0.10;
    public double rateFaintBonusMax     = 0.20;

    public boolean customExpEnabledAll          = true;
    /** Bật/tắt thưởng EXP khi hạ gục. Mặc định true, tự động tắt nếu Cobblemon exp multiplier > 0 */
    public boolean customExpEnabledFaintBonus   = true;

    // ============================================================
    // Singleton
    // ============================================================
    static BattleGrowthConfig instance;

    public static BattleGrowthConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    // ============================================================
    // Load / Save
    // ============================================================

    public static BattleGrowthConfig load() {
        BattleGrowthConfig defaults = new BattleGrowthConfig();

        if (!Files.exists(CONFIG_PATH)) {
            // Chưa có file → tạo mới với toàn bộ defaults
            save(defaults);
            System.out.println("[BattleGrowth] Tạo file config mới: " + CONFIG_PATH);
            return defaults;
        }

        // Đọc file hiện tại
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            System.err.println("[BattleGrowth] Lỗi đọc config, dùng defaults: " + e.getMessage());
            save(defaults);
            return defaults;
        }

        // Deserialize những gì có, giữ nguyên defaults cho key còn thiếu
        BattleGrowthConfig loaded = defaults;
        boolean dirty = false;

        // Mỗi field: nếu tồn tại trong json thì đọc, không thì giữ default và đánh dấu dirty
        JsonObject defaultJson = (JsonObject) GSON.toJsonTree(defaults);
        for (String key : defaultJson.keySet()) {
            if (!json.has(key)) {
                System.out.println("[BattleGrowth] Config thiếu key '" + key + "', thêm default: " + defaultJson.get(key));
                json.add(key, defaultJson.get(key));
                dirty = true;
            }
        }

        // Parse lại sau khi đã bổ sung key thiếu
        loaded = GSON.fromJson(json, BattleGrowthConfig.class);

        if (dirty) {
            // Ghi lại file với các key mới được bổ sung
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(json, writer);
                System.out.println("[BattleGrowth] Đã bổ sung key còn thiếu vào config.");
            } catch (Exception e) {
                System.err.println("[BattleGrowth] Lỗi ghi config: " + e.getMessage());
            }
        }

        return loaded;
    }

    public static void save(BattleGrowthConfig cfg) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(cfg, writer);
            }
        } catch (Exception e) {
            System.err.println("[BattleGrowth] Lỗi lưu config: " + e.getMessage());
        }
    }

    // ============================================================
    // Áp dụng config vào ExpConfig + tắt/bật EXP Cobblemon
    // ============================================================

    public void apply() {
        // Ghi vào ExpConfig
        ExpConfig.RATE_BASE_HIT_MIN_VAL    = rateBaseHitMin;
        ExpConfig.RATE_BASE_HIT_MAX_VAL    = rateBaseHitMax;
        ExpConfig.RATE_COMBO_BONUS_VAL     = rateComboBonus;
        ExpConfig.RATE_MISS_VAL            = rateMiss;
        ExpConfig.RATE_BUFF_VAL            = rateBuff;
        ExpConfig.RATE_DEBUFF_VAL          = rateDebuff;
        ExpConfig.RATE_MISC_VAL            = rateMisc;
        ExpConfig.OVER_BUDGET_EXP_VAL      = overBudgetExp;
        ExpConfig.RATE_FAINT_BONUS_MIN_VAL = rateFaintBonusMin;
        ExpConfig.RATE_FAINT_BONUS_MAX_VAL = rateFaintBonusMax;
        ExpConfig.setCustomExpEnabledAll(customExpEnabledAll);
        ExpConfig.setCustomExpEnabledFaintBonus(customExpEnabledFaintBonus);

        // Đồng bộ với Cobblemon: nếu customExpEnabledFaintBonus = true thì tắt EXP mặc định
        // để tránh người chơi nhận EXP 2 lần
        syncCobblemonExp();
    }

    /**
     * Tắt EXP mặc định của Cobblemon nếu mod đang bật faint bonus,
     * hoặc bật lại nếu mod tắt hoàn toàn.
     */
    public void syncCobblemonExp() {
        try {
            var config = com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig();
            if (!customExpEnabledAll) {
                // Mod tắt hết → trả lại EXP mặc định Cobblemon
                config.setExperienceMultiplier(1.0f);
                System.out.println("[BattleGrowth] Mod EXP tắt → bật lại EXP mặc định Cobblemon (x1.0)");
            } else if (customExpEnabledFaintBonus) {
                // Mod đang xử lý EXP → tắt EXP mặc định Cobblemon để tránh cộng 2 lần
                config.setExperienceMultiplier(0.0f);
                System.out.println("[BattleGrowth] Mod EXP bật → tắt EXP mặc định Cobblemon (x0)");
            }
        } catch (Exception e) {
            System.err.println("[BattleGrowth] Không thể sync EXP Cobblemon: " + e.getMessage());
        }
    }
}