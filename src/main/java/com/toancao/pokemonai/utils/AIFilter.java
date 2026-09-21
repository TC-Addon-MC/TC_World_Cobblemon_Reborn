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

        if (pkmn.getOwnerUUID() != null) return false;

        if (pokemonEntity.getBattleId() != null) return false;
        
        if (pokemonEntity.isSleeping()) return false;
        if (pokemonEntity.isBusy()) return false;
        if (pokemonEntity.isVehicle()) return false;

        if (pokemonEntity.getTarget() != null) return false;

        if (com.toancao.pokemonai.compat.FightOrFlightCompat.isEngaged(pokemonEntity)) {
            return false;
        }

        if (pokemonEntity.level() == null) return false;

        if (!com.toancao.pokemonai.api.PokemonAIEvents.ON_AI_FILTER_CHECK.invoker().onAIFilterCheck(pokemonEntity)) {
            return false;
        }

        return true;
    }
}
