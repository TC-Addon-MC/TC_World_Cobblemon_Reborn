package com.toancao.flyingspawn;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

/**
 * Kiểm tra ngữ cảnh (context) của Pokemon trước khi áp dụng hành vi bay.
 *
 * Hệ thống bay CHỈ hoạt động khi Pokemon:
 *   1. Là hoang dã (wild) — không thuộc trainer
 *   2. Không đang trong battle
 *   3. Không đang trong cinematic / capture / recall
 *
 * Khi bất kỳ điều kiện nào vi phạm, FlightStateMachine sẽ bị tạm dừng
 * và behavior mặc định của Cobblemon được giữ nguyên.
 */
public class FlightContext {

    /**
     * Trả về true nếu Pokemon này hiện tại ĐỦ ĐIỀU KIỆN để bay tự do.
     */
    public static boolean isEligible(PokemonEntity pokemon) {
        if (pokemon == null || !pokemon.isAlive() || pokemon.isRemoved()) return false;

        Pokemon pkmn = pokemon.getPokemon();

        // 1. Phải là wild — không thuộc về bất kỳ trainer nào
        if (pkmn.getOwnerUUID() != null) return false;

        // 2. Không đang trong battle
        if (pokemon.getBattleId() != null) return false;
        if (pokemon.isSleeping()) return false;
        // 3. Entity phải tồn tại trong world hợp lệ
        if (pokemon.level() == null) return false;

        return true;
    }

    /**
     * Kiểm tra ngắn gọn — dùng trong tick loop để quyết định có chạy FSM không.
     * Nếu false → skip tick, không can thiệp vào movement.
     */
    public static boolean shouldTick(PokemonEntity pokemon) {
        return isEligible(pokemon);
    }
}