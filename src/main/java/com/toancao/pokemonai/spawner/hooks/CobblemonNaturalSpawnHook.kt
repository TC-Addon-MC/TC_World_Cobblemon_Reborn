package com.toancao.pokemonai.spawner.hooks

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.GenericPackSpawner
import com.toancao.pokemonai.spawner.PackSpawnRegistry
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.utils.EntityUtils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.server.level.ServerLevel

object CobblemonNaturalSpawnHook {

    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, level ->
            if (entity !is PokemonEntity || level !is ServerLevel) return@register

            // QUY TẮC ANTI-LOOP: Bỏ qua nếu entity đã thuộc về đàn hoặc đã được gắn tag
            if (entity.tags.contains("tc_herd_entity") || entity.getHerdData().isInHerd) {
                return@register
            }

            // Bỏ qua nếu Pokemon không phải Pokemon hoang dã
            if (!EntityUtils.isWild(entity)) return@register

            val species = entity.pokemon.species.name.lowercase()
            val factory = PackSpawnRegistry.getFactory(species) ?: return@register

            // Đọc xác suất sinh bầy tự nhiên từ file config JSON
            val herdChance = com.toancao.pokemonai.config.HerdConfigManager.getConfigForSpecies(species).naturalSpawnHerdChance
            if (level.random.nextFloat() > herdChance) return@register

            // Đánh dấu entity gốc đã được xử lý để chống đệ quy
            entity.addTag("tc_herd_entity")

            // Tạo đàn dựa trên vị trí của entity gốc
            val params = factory(level, entity.position())

            // Xóa cá thể đơn lẻ ban đầu và thay thế bằng toàn bộ đàn hoàn chỉnh
            entity.discard()
            GenericPackSpawner.spawnPokemonPack(params)
        }
    }
}
