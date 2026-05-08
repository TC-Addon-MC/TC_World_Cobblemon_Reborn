package com.toancao.battlegrowth.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokemonBattle.class, remap = false)
public class BattleHintFilterMixin {

    /**
     * Chặn các message dạng [Hint] từ Showdown engine khỏi chat battle.
     * Showdown tự sinh ra những dòng này để debug move logic (vd: "Fake Out only works on first turn"),
     * nhưng chúng không nên hiển thị với người chơi.
     */
    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true, remap = false)
    private void filterHintMessages(Component message, CallbackInfo ci) {
        String raw = message.getString();
        if (raw == null) return;

        // Lọc các prefix do Showdown sinh ra, không có giá trị với người chơi
        if (raw.startsWith("[Hint]")
                || raw.startsWith("[Invalid]")
                || raw.startsWith("[Unhandled]")
                || raw.startsWith("[Unavailable choice]")) {
            ci.cancel();
        }
    }
}
