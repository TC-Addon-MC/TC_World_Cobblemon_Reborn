package com.toancao.pokemonai.flight

import net.minecraft.resources.ResourceLocation

object CustomFlightRegistry {
    private val profiles = mutableMapOf<ResourceLocation, List<FlightConfig>>()

    fun register(species: ResourceLocation, vararg configs: FlightConfig) {
        profiles[species] = configs.toList()
    }

    fun getConfigs(species: ResourceLocation): List<FlightConfig>? {
        return profiles[species]
    }
    
    fun hasConfig(species: ResourceLocation): Boolean {
        return profiles.containsKey(species)
    }

    /** Trả về list tên loài đã đăng ký (chỉ lấy path, ví dụ "pidgey", "charizard") */
    fun getAllSpeciesNames(): List<String> {
        return profiles.keys.map { it.path }.sorted()
    }
}

