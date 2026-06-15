@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.utils

import com.cobblemon.mod.common.api.scheduling.after
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.compat.CobblemonBridge
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource

object EvolutionEffectUtils {

    // Timing khớp với Evolution.kt của Cobblemon:
    // 1s  → bắt đầu animation bedrock 'evolution'
    // 11.2s → đổi loài (onEvolve)
    // 12s → animation 'cry' + kết thúc (onComplete)
    private const val ANIM_START_SECONDS = 1F
    private const val SPECIES_CHANGE_SECONDS = 11.2F
    private const val COMPLETE_SECONDS = 12F

    fun playEvolutionSequence(
        pokemon: PokemonEntity,
        level: ServerLevel,
        onEvolve: () -> Unit,
        onComplete: () -> Unit
    ) {
        // Lock entity: không di chuyển, không bị capture, không bị đẩy
        CobblemonBridge.setEvolutionStarted(pokemon, true)
        CobblemonBridge.stopNavigation(pokemon)

        // Phase 1: Phát Bedrock animation evolution (giống hệt Cobblemon gốc)

        pokemon.after(ANIM_START_SECONDS) {
            CobblemonBridge.sendPosableAnimationPacket(
                CobblemonBridge.getId(pokemon),
                "q.bedrock_stateful('evolution', 'evolution', 'endures_primary_animations');",
                CobblemonBridge.getX(pokemon),
                CobblemonBridge.getY(pokemon),
                CobblemonBridge.getZ(pokemon),
                level
            )

            // Phát sound evolution của Cobblemon (thay vì BEACON_ACTIVATE)
            CobblemonBridge.playEvolutionSound(
                level,
                CobblemonBridge.getBlockPos(pokemon),
                1F,
                1F
            )
        }

        // Phase 2: Đổi loài tại đỉnh animation (~11.2s)
        pokemon.after(SPECIES_CHANGE_SECONDS) {
            onEvolve()
        }

        // Phase 3: Cry animation + unlock entity + kết thúc (~12s)
        pokemon.after(COMPLETE_SECONDS) {
            CobblemonBridge.sendPosableAnimationPacket(
                CobblemonBridge.getId(pokemon),
                "cry",
                CobblemonBridge.getX(pokemon),
                CobblemonBridge.getY(pokemon),
                CobblemonBridge.getZ(pokemon),
                level
            )

            CobblemonBridge.setEvolutionStarted(pokemon, false)
            onComplete()
        }
    }
}
