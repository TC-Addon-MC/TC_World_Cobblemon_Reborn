package com.toancao.pokemonai.attachment

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class EvolutionStateData(
    var dragonGatePassed: Boolean = false
) {
    companion object {
        val CODEC: Codec<EvolutionStateData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.BOOL.fieldOf("dragonGatePassed").forGetter(EvolutionStateData::dragonGatePassed)
            ).apply(instance, ::EvolutionStateData)
        }
    }
}



