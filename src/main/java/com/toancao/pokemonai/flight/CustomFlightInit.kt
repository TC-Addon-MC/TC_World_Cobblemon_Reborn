package com.toancao.pokemonai.flight

import net.minecraft.resources.ResourceLocation

/**
 * Nơi để cấu hình và truyền các Pokemon có khả năng bay (Flight)
 */
object CustomFlightInit {
    
    fun registerDefaultFlyingPokemon() {
        com.toancao.pokemonai.config.FlightConfigManager.loadConfig()

        com.toancao.pokemonai.config.FlightConfigManager.pokemonFlightConfigs.forEach { (species, config) ->
            val resourceLocation = ResourceLocation.tryParse(species)
            if (resourceLocation != null) {
                CustomFlightRegistry.register(resourceLocation, config)
            }
        }
    }
}
