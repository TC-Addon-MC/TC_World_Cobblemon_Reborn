package com.toancao.pokemonai.utils;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

/**
 * Lọc các điều kiện của Pokemon trước khi cho phép AI tuỳ chỉnh (như nhảy, bay) hoạt động.
 * Đảm bảo Pokemon không bị gián đoạn khi đang ngủ, đang chiến đấu, hoặc đã bị thu phục.
 */
public class AIFilter {
    public static boolean isEligible(PokemonEntity pokemonEntity) {
        if (!pokemonEntity.isAlive() || pokemonEntity.isRemoved()) return false;

        Pokemon pkmn = pokemonEntity.getPokemon();
        if (pkmn == null) return false;

        // 1. Phải là hoang dã (wild) — không thuộc về bất kỳ trainer nào
        if (pkmn.getOwnerUUID() != null) return false;

        // 2. Không đang trong battle
        if (pokemonEntity.getBattleId() != null) return false;
        
        // 3. Không đang bận rộn với các hành động khác
        if (pokemonEntity.isSleeping()) return false;
        if (pokemonEntity.isBusy()) return false;
        if (pokemonEntity.isVehicle()) return false;

        // 4. Không đang đánh nhau hoặc bị tấn công gần đây
        if (pokemonEntity.getTarget() != null) return false;
        if (pokemonEntity.getLastHurtByMob() != null && pokemonEntity.tickCount - pokemonEntity.getLastHurtByMobTimestamp() < 300) {
            return false; // Chờ 15 giây (300 tick) sau khi bị đánh
        }

        // 5. Entity phải tồn tại trong world hợp lệ
        if (pokemonEntity.level() == null) return false;

        return true;
    }
}
