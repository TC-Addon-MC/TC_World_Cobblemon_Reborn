package com.toancao.pokemonai.spawner.resolver

import com.toancao.pokemonai.spawner.FormationType
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

interface PackPositionResolver {
    fun resolvePositions(
        level: ServerLevel,
        center: Vec3,
        count: Int,
        radius: Double,
        formation: FormationType
    ): List<Vec3>
}
