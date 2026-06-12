package com.toancao.pokemonai.utils

import net.minecraft.world.entity.Entity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

object WorldUtils {
    fun isDay(world: Level): Boolean {
        return world.isDay
    }

    fun isNight(world: Level): Boolean {
        return world.isNight
    }

    fun isRaining(world: Level, pos: BlockPos): Boolean {
        return world.isRaining && world.getBiome(pos).value().hasPrecipitation()
    }

    fun getBiome(world: Level, pos: BlockPos): String {
        return world.getBiome(pos).unwrapKey().map { it.location().toString() }.orElse("unknown")
    }

    fun <T : Entity> getNearestEntity(entity: Entity, radius: Double, clazz: Class<T>): T? {
        val box = entity.boundingBox.inflate(radius)
        val entities = entity.level().getEntitiesOfClass(clazz, box) { it != entity }
        return entities.minByOrNull { it.distanceToSqr(entity) }
    }
}
