package com.toancao.battlegrowth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracker dùng chung:
 * - overrides: level server gửi về
 * - appliedCache: level đã apply ở client (tránh set mỗi frame)
 * - maxHpOverrides: maxHp mới sau khi level up (để client patch HP bar)
 */
public class LevelOverrideTracker {

    private static final Map<UUID, Integer> overrides = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> appliedCache = new ConcurrentHashMap<>();

    // 🔥 maxHp mới từ server sau khi lên cấp
    private static final Map<UUID, Integer> maxHpOverrides = new ConcurrentHashMap<>();
    // 🔥 maxHp đã apply ở client (tránh set mỗi frame)
    private static final Map<UUID, Integer> appliedMaxHpCache = new ConcurrentHashMap<>();

    public static void setLevel(UUID pokemonUuid, int newLevel) {
        overrides.put(pokemonUuid, newLevel);
    }

    public static int getLevel(UUID pokemonUuid) {
        return overrides.getOrDefault(pokemonUuid, -1);
    }

    public static int getCachedLevel(UUID pokemonUuid) {
        return appliedCache.getOrDefault(pokemonUuid, -1);
    }

    public static void setCachedLevel(UUID pokemonUuid, int level) {
        appliedCache.put(pokemonUuid, level);
    }

    /** Server ghi maxHp mới sau khi pokemon lên cấp */
    public static void setMaxHp(UUID pokemonUuid, int newMaxHp) {
        maxHpOverrides.put(pokemonUuid, newMaxHp);
    }

    /** Client đọc maxHp override */
    public static int getMaxHp(UUID pokemonUuid) {
        return maxHpOverrides.getOrDefault(pokemonUuid, -1);
    }

    /** maxHp đã apply ở client */
    public static int getCachedMaxHp(UUID pokemonUuid) {
        return appliedMaxHpCache.getOrDefault(pokemonUuid, -1);
    }

    /** Ghi cache maxHp sau khi apply */
    public static void setCachedMaxHp(UUID pokemonUuid, int maxHp) {
        appliedMaxHpCache.put(pokemonUuid, maxHp);
    }

    public static void clear(UUID pokemonUuid) {
        overrides.remove(pokemonUuid);
        appliedCache.remove(pokemonUuid);
        maxHpOverrides.remove(pokemonUuid);
        appliedMaxHpCache.remove(pokemonUuid);
    }

    public static void clearAll() {
        overrides.clear();
        appliedCache.clear();
        maxHpOverrides.clear();
        appliedMaxHpCache.clear();
    }
}