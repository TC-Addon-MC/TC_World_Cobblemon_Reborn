package com.toancao.pokemonai.spawner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.spawner.hierarchy.HerdRole
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.spawner.hierarchy.setHerdData
import com.toancao.pokemonai.spawner.resolver.AirPackPositionResolver
import com.toancao.pokemonai.spawner.resolver.LandPackPositionResolver
import com.toancao.pokemonai.spawner.resolver.PackPositionResolver
import com.toancao.pokemonai.spawner.resolver.WaterPackPositionResolver
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.random.Random

object GenericPackSpawner {

    fun spawnPokemonPack(params: PackSpawnParams): PackSpawnResult? {
        val level = params.level
        val herdId = UUID.randomUUID()

        // 1. Kiểm tra Chunk Cap Protection
        val chunkX = params.centerPos.x.toInt() shr 4
        val chunkZ = params.centerPos.z.toInt() shr 4
        val minX = (chunkX shl 4).toDouble()
        val minZ = (chunkZ shl 4).toDouble()
        val chunkBox = AABB(minX, -64.0, minZ, minX + 16.0, 320.0, minZ + 16.0)
        val pokemonInChunk = level.getEntitiesOfClass(PokemonEntity::class.java, chunkBox)
        if (pokemonInChunk.size >= Cobblemon.config.pokemonPerChunk + 8) {
            return null
        }

        // 2. Tính Level Leader (có cân bằng theo Player Party gần nhất)
        val leaderLevel = calculateLeaderLevel(level, params)

        // 3. Tạo ĐẦU ĐÀN (LEADER)
        val leaderPoke = PokemonProperties.parse("species=${params.species} level=$leaderLevel").create()
        val leaderEntity = PokemonEntity(level, leaderPoke)
        leaderEntity.setPos(params.centerPos.x, params.centerPos.y, params.centerPos.z)
        applyRole(
            entity = leaderEntity,
            role = HerdRole.LEADER,
            scale = params.hierarchyRule.leaderScale,
            herdId = herdId,
            leaderUUID = leaderEntity.uuid,
            fIdx = 0,
            extraTags = params.customTags
        )
        if (!level.addFreshEntity(leaderEntity)) return null

        // 4. Sinh các con ĐÀN EM (Members)
        val totalCount = params.countRange.random()
        val memberCount = (totalCount - 1).coerceAtLeast(1)

        val resolver = getResolver(params.movementType)
        val positions = resolver.resolvePositions(level, params.centerPos, memberCount, params.spawnRadius, params.formation)

        val members = mutableListOf<PokemonEntity>()
        for (i in 0 until memberCount) {
            val memLvl = (leaderLevel - params.followerLevelDelta.random()).coerceAtLeast(1)
            val memPoke = PokemonProperties.parse("species=${params.species} level=$memLvl").create()
            val memEntity = PokemonEntity(level, memPoke)
            val pos = positions.getOrElse(i) { params.centerPos }
            memEntity.setPos(pos.x, pos.y, pos.z)
            val scale = Random.nextDouble(params.hierarchyRule.memberScaleRange.start.toDouble(), params.hierarchyRule.memberScaleRange.endInclusive.toDouble()).toFloat()
            applyRole(
                entity = memEntity,
                role = HerdRole.MEMBER,
                scale = scale,
                herdId = herdId,
                leaderUUID = leaderEntity.uuid,
                fIdx = i + 1, // Thứ tự đàn em 1, 2, 3, 4...
                extraTags = params.customTags
            )
            if (level.addFreshEntity(memEntity)) {
                members.add(memEntity)
            }
        }

        val allPack = mutableListOf(leaderEntity).apply {
            addAll(members)
        }
        params.postSpawnCallback?.invoke(leaderEntity, allPack)
        com.toancao.pokemonai.spawner.hooks.PackSpawnEvents.ON_PACK_SPAWNED.invoker().onPackSpawned(herdId, leaderEntity, allPack)

        return PackSpawnResult(herdId, leaderEntity, null, members, emptyList(), allPack.size)
    }

    private fun applyRole(
        entity: PokemonEntity,
        role: HerdRole,
        scale: Float,
        herdId: UUID,
        leaderUUID: UUID,
        fIdx: Int,
        extraTags: List<String>
    ) {
        val data = entity.getHerdData()
        data.herdId = herdId
        data.role = role
        data.leaderUUID = leaderUUID
        data.formationIndex = fIdx
        entity.setHerdData(data)

        // Gán Scale qua Minecraft Generic Attribute
        entity.attributes.getInstance(Attributes.SCALE)?.baseValue = scale.toDouble()

        // Gán NameTag phân cấp (tắt hiển thị mặc định để giữ mỹ quan tự nhiên)
        if (role == HerdRole.LEADER) {
            entity.customName = net.minecraft.network.chat.Component.literal("§6👑 [ĐẦU ĐÀN]")
            entity.isCustomNameVisible = false
        } else {
            entity.customName = net.minecraft.network.chat.Component.literal("§a🐂 [ĐÀN EM #$fIdx]")
            entity.isCustomNameVisible = false
        }

        // Gán Tags phân loại
        entity.addTag(role.roleTag)
        entity.addTag("tc_herd_entity")
        extraTags.forEach { entity.addTag(it) }
    }

    private fun calculateLeaderLevel(level: ServerLevel, params: PackSpawnParams): Int {
        if (!params.scaleWithPlayerParty) return params.leaderLevelRange.random()
        val nearestPlayer = level.getNearestPlayer(params.centerPos.x, params.centerPos.y, params.centerPos.z, 64.0, false) as? net.minecraft.server.level.ServerPlayer
            ?: return params.leaderLevelRange.random()
        val party = Cobblemon.storage.getParty(nearestPlayer)
        val playerMaxLevel = party.map { it.level }.maxOrNull() ?: return params.leaderLevelRange.random()
        return (playerMaxLevel + Random.nextInt(-2, 4)).coerceIn(params.leaderLevelRange)
    }

    private fun getResolver(type: PackMovementType): PackPositionResolver = when (type) {
        PackMovementType.LAND -> LandPackPositionResolver
        PackMovementType.AIR -> AirPackPositionResolver
        PackMovementType.AQUATIC -> WaterPackPositionResolver
    }
}
