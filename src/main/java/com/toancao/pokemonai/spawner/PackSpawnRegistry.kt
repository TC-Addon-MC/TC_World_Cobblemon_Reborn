package com.toancao.pokemonai.spawner

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

typealias PackParamsFactory = (level: ServerLevel, centerPos: Vec3) -> PackSpawnParams

object PackSpawnRegistry {
    private val registry = ConcurrentHashMap<String, PackParamsFactory>()

    fun register(species: String, factory: PackParamsFactory) {
        registry[species.lowercase()] = factory
    }

    fun getFactory(species: String): PackParamsFactory? {
        return registry[species.lowercase()]
    }

    fun isPackSpecies(species: String): Boolean {
        return registry.containsKey(species.lowercase())
    }
}
