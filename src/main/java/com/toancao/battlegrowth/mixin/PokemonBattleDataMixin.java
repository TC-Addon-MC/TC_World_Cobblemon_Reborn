package com.toancao.battlegrowth.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.toancao.battlegrowth.util.IBattleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = PokemonBattle.class, remap = false)
public class PokemonBattleDataMixin implements IBattleData {

    @Unique private final ConcurrentHashMap<UUID, Double> budgetTracker = new ConcurrentHashMap<>();
    @Unique private final ConcurrentHashMap<UUID, Integer> hpTracker = new ConcurrentHashMap<>();
    @Unique
    private String battleDebugId;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.battleDebugId = Integer.toHexString(this.hashCode());
        System.out.println(">>> [DEBUG] Battle " + battleDebugId + " khởi tạo: Tạo mới Map data riêng biệt.");
    }

    @Override
    public ConcurrentHashMap<UUID, Double> getBudgetTracker() { return budgetTracker; }

    @Override
    public ConcurrentHashMap<UUID, Integer> getHpTracker() { return hpTracker; }

    @Override
    public String getBattleDebugId() { return battleDebugId; }

    // Phương thức này sẽ chạy khi Garbage Collector hốt object này đi
    @Override
    @SuppressWarnings("removal")
    protected void finalize() throws Throwable {
        System.out.println("<<< [DEBUG] Battle " + battleDebugId + " bị GC giải phóng: Data tự động biến mất!");
        super.finalize();
    }
}