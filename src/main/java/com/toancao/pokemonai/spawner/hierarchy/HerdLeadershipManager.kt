package com.toancao.pokemonai.spawner.hierarchy

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import java.util.UUID

object HerdLeadershipManager {

    /**
     * Tìm tất cả thành viên trong đàn trong phạm vi quanh tâm
     */
    fun findPackMembers(level: ServerLevel, herdId: UUID, searchBox: AABB): List<PokemonEntity> {
        return level.getEntitiesOfClass(PokemonEntity::class.java, searchBox) { entity ->
            val data = entity.getHerdData()
            data.herdId == herdId
        }
    }

    /**
     * Tìm con đầu đàn của bầy.
     */
    fun findLeader(level: ServerLevel, herdId: UUID, searchBox: AABB): PokemonEntity? {
        return level.getEntitiesOfClass(PokemonEntity::class.java, searchBox) { entity ->
            val data = entity.getHerdData()
            data.herdId == herdId && data.isLeader
        }.firstOrNull()
    }
}
