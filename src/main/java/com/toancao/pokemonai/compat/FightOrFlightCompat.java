package com.toancao.pokemonai.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.lang.reflect.Method;

public class FightOrFlightCompat {
    private static boolean initialized = false;
    private static boolean hasFightOrFlight = false;
    private static Method getTargetMethod = null;
    private static Method shouldFightTargetMethod = null;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> utilsClass = Class.forName("me.rufia.fightorflight.utils.PokemonUtils");
            getTargetMethod = utilsClass.getMethod("getTarget", PokemonEntity.class);
            shouldFightTargetMethod = utilsClass.getMethod("shouldFightTarget", PokemonEntity.class);
            hasFightOrFlight = true;
            System.out.println("[PokemonAI] Fight or Flight mod detected! Integration enabled.");
        } catch (Exception e) {
            hasFightOrFlight = false;
            // No mod installed or version mismatch
        }
    }

    public static boolean isEngaged(PokemonEntity pokemon) {
        init();
        if (!hasFightOrFlight) return false;
        
        try {
            if (getTargetMethod != null) {
                Object target = getTargetMethod.invoke(null, pokemon);
                if (target != null) {
                    if (target instanceof java.util.Optional<?> opt) {
                        if (opt.isPresent()) return true;
                    } else if (target instanceof net.minecraft.world.entity.Entity) {
                        return true;
                    }
                    // Bỏ qua các kiểu trả về khác (ví dụ Boolean) để không bị lỗi return true liên tục
                }
            }
            // Tạm thời bỏ qua shouldFightTarget vì hàm này có thể chỉ dùng để check xem loài đó có tính hiếu chiến không (luôn true với thú dữ)
            // if (shouldFightTargetMethod != null) {
            //     Object shouldFight = shouldFightTargetMethod.invoke(null, pokemon);
            //     if (shouldFight instanceof Boolean b && b) return true;
            // }
        } catch (Throwable e) {
            // Ignore reflection errors during runtime
        }
        return false;
    }
}
