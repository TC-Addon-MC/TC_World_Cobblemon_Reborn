package com.toancao.pokemonai.spawner.hooks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.HerdRole
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.spawner.hierarchy.setHerdData
import com.toancao.pokemonai.utils.EntityUtils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import java.util.UUID
import java.util.WeakHashMap

/**
 * Maps Cobblemon 1.8 native herds to TC metadata without replacing or duplicating
 * entities created by the native spawning pipeline.
 */
object CobblemonNaturalSpawnHook {
    private const val SYNC_INTERVAL_TICKS = 20L
    private val nativeHerdSpecies = setOf("tauros", "bouffalant")
    private val pendingByLevel = WeakHashMap<ServerLevel, MutableMap<UUID, Long>>()

    fun register() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            enqueue(event.entity)
        }
        CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe { event ->
            enqueue(event.pokemonEntity)
        }
        ServerTickEvents.END_WORLD_TICK.register(::processPending)
    }

    private fun enqueue(entity: PokemonEntity) {
        val level = entity.level() as? ServerLevel ?: return
        if (!EntityUtils.isWild(entity)) return
        if (entity.pokemon.species.name.lowercase() !in nativeHerdSpecies) return

        pendingByLevel.getOrPut(level) { mutableMapOf() }[entity.uuid] = level.gameTime
    }

    private fun processPending(level: ServerLevel) {
        val pending = pendingByLevel[level] ?: return
        val iterator = pending.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entityId = entry.key
            val entity = level.getEntity(entityId) as? PokemonEntity
            if (entity == null || !entity.isAlive) {
                iterator.remove()
                continue
            }
            if (level.gameTime < entry.value) continue

            val data = entity.getHerdData()
            if (data.isInHerd && !data.isNativeHerd) {
                iterator.remove()
                continue
            }
            mapNativeFollower(level, entity)
            entry.setValue(level.gameTime + SYNC_INTERVAL_TICKS)
        }

        if (pending.isEmpty()) pendingByLevel.remove(level)
    }

    private fun mapNativeFollower(level: ServerLevel, follower: PokemonEntity): Boolean {
        val leaderId = follower.brain.getMemory(CobblemonMemories.HERD_LEADER)
            .orElse(null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return false
        val leader = level.getEntity(leaderId) as? PokemonEntity ?: return false
        if (!leader.isAlive || !EntityUtils.isWild(leader)) return false

        val herdId = leader.uuid
        applyNativeRole(leader, herdId, HerdRole.LEADER, 0)
        applyNativeRole(follower, herdId, HerdRole.MEMBER, formationIndex(follower.uuid))
        return true
    }

    private fun applyNativeRole(entity: PokemonEntity, herdId: UUID, role: HerdRole, formationIndex: Int) {
        val data = entity.getHerdData()
        data.herdId = herdId
        data.role = role
        data.leaderUUID = herdId
        data.formationIndex = formationIndex
        data.isNativeHerd = true
        entity.setHerdData(data)
        entity.addTag("tc_herd_entity")
        entity.removeTag(if (role == HerdRole.LEADER) HerdRole.MEMBER.roleTag else HerdRole.LEADER.roleTag)
        entity.addTag(role.roleTag)
    }

    private fun formationIndex(entityId: UUID): Int {
        return 1 + ((entityId.hashCode().toLong() and 0x7FFFFFFF) % 12).toInt()
    }
}
