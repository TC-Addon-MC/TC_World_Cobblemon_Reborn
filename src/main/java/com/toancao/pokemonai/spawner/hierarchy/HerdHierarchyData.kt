package com.toancao.pokemonai.spawner.hierarchy

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.toancao.pokemonai.attachment.PokemonAttachments
import net.minecraft.core.UUIDUtil
import java.util.Optional
import java.util.UUID

data class HerdHierarchyData(
    var herdId: UUID? = null,
    var role: HerdRole = HerdRole.MEMBER,
    var leaderUUID: UUID? = null,
    var partnerUUID: UUID? = null,
    var formationIndex: Int = 0,
    var isNativeHerd: Boolean = false,
    var isStampeding: Boolean = false,
    var stampedeDelayTicks: Int = 0,
    var stampedeTicksRemaining: Int = 0,
    var stampedeDirX: Double = 0.0,
    var stampedeDirZ: Double = 0.0
) {
    val isLeader: Boolean get() = role == HerdRole.LEADER
    val isInHerd: Boolean get() = herdId != null

    companion object {
        val CODEC: Codec<HerdHierarchyData> = RecordCodecBuilder.create { instance ->
            instance.group(
                UUIDUtil.CODEC.optionalFieldOf("herdId").forGetter { Optional.ofNullable(it.herdId) },
                Codec.STRING.optionalFieldOf("role", "MEMBER").forGetter { it.role.name },
                UUIDUtil.CODEC.optionalFieldOf("leaderUUID").forGetter { Optional.ofNullable(it.leaderUUID) },
                UUIDUtil.CODEC.optionalFieldOf("partnerUUID").forGetter { Optional.ofNullable(it.partnerUUID) },
                Codec.INT.optionalFieldOf("formationIndex", 0).forGetter { it.formationIndex },
                Codec.BOOL.optionalFieldOf("isNativeHerd", false).forGetter { it.isNativeHerd }
            ).apply(instance) { herdIdOpt, roleStr, leaderOpt, partnerOpt, fIdx, nativeHerd ->
                HerdHierarchyData(
                    herdId = herdIdOpt.orElse(null),
                    role = runCatching { HerdRole.valueOf(roleStr) }.getOrDefault(HerdRole.MEMBER),
                    leaderUUID = leaderOpt.orElse(null),
                    partnerUUID = partnerOpt.orElse(null),
                    formationIndex = fIdx,
                    isNativeHerd = nativeHerd
                )
            }
        }
    }
}

/**
 * Hàm tiện ích mở rộng truy xuất dữ liệu bầy đàn trên PokemonEntity
 */
fun PokemonEntity.getHerdData(): HerdHierarchyData {
    return this.getAttachedOrCreate(PokemonAttachments.HERD_DATA) { HerdHierarchyData() }
}

fun PokemonEntity.setHerdData(data: HerdHierarchyData) {
    this.setAttached(PokemonAttachments.HERD_DATA, data)
}
