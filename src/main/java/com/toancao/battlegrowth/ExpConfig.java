package com.toancao.battlegrowth;

import com.cobblemon.mod.common.Cobblemon;
import java.util.Random;

public class ExpConfig {
    static {
        System.out.println("BattleGrowth mod đã khởi động");
    }

    private static final Random random = new Random();

    // ============================================================
    // Các giá trị runtime — được ghi bởi BattleGrowthConfig.apply()
    // ============================================================
    public static double RATE_BASE_HIT_MIN_VAL    = 0.05;
    public static double RATE_BASE_HIT_MAX_VAL    = 0.15;
    public static double RATE_COMBO_BONUS_VAL     = 0.01;
    public static double RATE_MISS_VAL            = 0.02;
    public static double RATE_BUFF_VAL            = 0.015;
    public static double RATE_DEBUFF_VAL          = 0.01;
    public static double RATE_MISC_VAL            = 0.005;
    public static int    OVER_BUDGET_EXP_VAL      = 10;
    public static double RATE_FAINT_BONUS_MIN_VAL = 0.10;
    public static double RATE_FAINT_BONUS_MAX_VAL = 0.20;

    private static boolean customExpEnabledAll        = true;
    private static boolean customExpEnabledFaintBonus = true;

    // Setters cho BattleGrowthConfig
    public static void setCustomExpEnabledAll(boolean v)        { customExpEnabledAll = v; }
    public static void setCustomExpEnabledFaintBonus(boolean v) { customExpEnabledFaintBonus = v; }

    public static boolean isCustomExpEnabledAll()        { return customExpEnabledAll; }
    public static boolean isCustomExpEnabledFaintBonus() { return customExpEnabledFaintBonus; }

    // ============================================================
    // Đồng bộ với Cobblemon config (giữ lại để dùng nội bộ nếu cần)
    // ============================================================
    public static void refreshExperienceSettings() {
        var config = Cobblemon.INSTANCE.getConfig();
        float currentMultiplier = config.getExperienceMultiplier();
        if (currentMultiplier > 0) {
            customExpEnabledFaintBonus = false;
        } else if (customExpEnabledFaintBonus) {
            config.setExperienceMultiplier(0.0f);
        }
    }

    // ============================================================
    // Công thức tính EXP
    // ============================================================

    public static int calcFaintBonus(int expToNextEvolution) {
        if (!customExpEnabledAll || !customExpEnabledFaintBonus) return 0;
        double rate = RATE_FAINT_BONUS_MIN_VAL + (random.nextDouble() * (RATE_FAINT_BONUS_MAX_VAL - RATE_FAINT_BONUS_MIN_VAL));
        int bonus = (int) (expToNextEvolution * rate);
        return Math.max(1, bonus);
    }

    public static double calculateBattleBudget(int totalLevelExp) {
        double factor = 2.0 + (random.nextDouble() * 2.0);
        return factor * totalLevelExp;
    }

    public static double getLevelDiffModifier(int myLevel, int foeLevel) {
        int diff = Math.abs(myLevel - foeLevel);
        if (diff < 10) return 0.0;
        int i = diff / 10;
        double mod = i * 0.10;
        return (myLevel < foeLevel) ? mod : -mod;
    }

    public static int applyModifier(double base, int myLevel, int foeLevel) {
        double mod = getLevelDiffModifier(myLevel, foeLevel);
        double result = base * (1.0 + mod);
        return (int) Math.max(1, result);
    }

    public static double baseHitExp(int expToNextEvolution) {
        if (!customExpEnabledAll) return 0;
        double rate = RATE_BASE_HIT_MIN_VAL + random.nextDouble() * (RATE_BASE_HIT_MAX_VAL - RATE_BASE_HIT_MIN_VAL);
        return expToNextEvolution * rate;
    }

    public static double comboHitExp(int expToNextEvolution) {
        if (!customExpEnabledAll) return 0;
        return baseHitExp(expToNextEvolution) + (expToNextEvolution * RATE_COMBO_BONUS_VAL);
    }

    public static int missExp(int expToNextEvolution) {
        if (!customExpEnabledAll) return 0;
        double res = expToNextEvolution * RATE_MISS_VAL;
        return (int) Math.max(1, res);
    }

    public static int debuffMoveExp(int totalLevelExp) {
        if (!customExpEnabledAll) return 0;
        double res = totalLevelExp * RATE_DEBUFF_VAL;
        return (int) Math.max(1, res);
    }

    public static int buffMoveExp(int totalLevelExp) {
        if (!customExpEnabledAll) return 0;
        double res = totalLevelExp * RATE_BUFF_VAL;
        return (int) Math.max(1, res);
    }

    public static int miscMoveExp(int expToNextEvolution) {
        if (!customExpEnabledAll) return 0;
        double res = expToNextEvolution * RATE_MISC_VAL;
        return (int) Math.max(1, res);
    }

    public static double attackerWeight(double damage, int maxHp) {
        return Math.pow(damage / (maxHp + 0.1), 1.5);
    }

    public static double defenderWeight(double damage, int maxHp) {
        return Math.pow(Math.max(0, maxHp - damage) / (maxHp + 0.1), 1.5);
    }

    public static int calcAttackerExp(double base, int attLevel, int foeLevel) {
        return applyModifier(base, attLevel, foeLevel);
    }

    public static int calcDefenderExp(double base, int attLevel, int foeLevel) {
        return applyModifier(base, foeLevel, attLevel);
    }

    public static int getOverBudgetExp() {
        return customExpEnabledAll ? OVER_BUDGET_EXP_VAL : 0;
    }
}