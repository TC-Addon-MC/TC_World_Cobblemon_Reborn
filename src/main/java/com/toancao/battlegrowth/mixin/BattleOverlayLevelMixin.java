package com.toancao.battlegrowth.mixin;

import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.toancao.battlegrowth.LevelOverrideTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(BattleGUI.class)
public class BattleOverlayLevelMixin {

    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void patchLevelBeforeRender(
            GuiGraphics context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci) {

        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;

        patchSide(battle.getSide1());
        patchSide(battle.getSide2());
    }

    private void patchSide(ClientBattleSide side) {
        if (side == null) return;
        for (ActiveClientBattlePokemon active : side.getActiveClientBattlePokemon()) {
            if (active == null) continue;
            ClientBattlePokemon cbp = active.getBattlePokemon();
            if (cbp == null) continue;
            patchPokemon(cbp);
        }
    }

    private void patchPokemon(ClientBattlePokemon cbp) {
        UUID uuid = cbp.getUuid();

        // --- Patch level ---
        int overrideLevel = LevelOverrideTracker.getLevel(uuid);
        if (overrideLevel > 0) {
            int cachedLevel = LevelOverrideTracker.getCachedLevel(uuid);
            if (cachedLevel != overrideLevel) {
                var props = cbp.getProperties();
                if (props != null) {
                    props.setLevel(overrideLevel);
                }
                LevelOverrideTracker.setCachedLevel(uuid, overrideLevel);
            }
        }

        // --- Patch maxHp ---
        // maxHp là Float var trong Kotlin → Java gọi setter trực tiếp cbp.setMaxHp(float)
        int overrideMaxHp = LevelOverrideTracker.getMaxHp(uuid);
        if (overrideMaxHp > 0) {
            int cachedMaxHp = LevelOverrideTracker.getCachedMaxHp(uuid);
            if (cachedMaxHp != overrideMaxHp) {
                cbp.setMaxHp((float) overrideMaxHp);
                LevelOverrideTracker.setCachedMaxHp(uuid, overrideMaxHp);
            }
        }
    }
}