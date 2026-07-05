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

        // 4. Không đang đánh nhau (đã target)
        if (pokemonEntity.getTarget() != null) return false;

        // 5. Tích hợp Fight or Flight (nếu có cài mod)
        if (com.toancao.pokemonai.compat.FightOrFlightCompat.isEngaged(pokemonEntity)) {
            return false;
        }

        // 6. Entity phải tồn tại trong world hợp lệ
        if (pokemonEntity.level() == null) return false;

        // 7. Mod khác có muốn chặn AI không?
        if (!com.toancao.pokemonai.api.PokemonAIEvents.ON_AI_FILTER_CHECK.invoker().onAIFilterCheck(pokemonEntity)) {
            return false;
        }

        return true;
    }
}
