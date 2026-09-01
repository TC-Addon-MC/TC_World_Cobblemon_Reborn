package com.toancao.pokemonai.spawner

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID

enum class PackMovementType {
    LAND,       // Động vật cạn (Tauros, Bouffalant, Mareep...)
    AIR,        // Động vật bay (Pidgeot, Talonflame...)
    AQUATIC     // Động vật bơi (Gyarados, Magikarp...)
}

enum class FormationType {
    SCATTERED,          // Tản mác tự nhiên xung quanh thủ lĩnh
    V_SHAPE,            // Đội hình chữ V
    PROTECTIVE_CIRCLE,  // Vòng tròn bảo vệ (Con non ở tâm)
    COLUMN              // Xếp hàng dọc
}

data class HierarchySpawnRule(
    val hasSubLeader: Boolean = true,
    val subLeaderMinPackSize: Int = 7,
    val juvenileRatio: Float = 0.2f,             // 20% cá thể trong đàn là con non
    val leaderScale: Float = 1.25f,
    val subLeaderScale: Float = 1.1f,
    val memberScaleRange: ClosedFloatingPointRange<Float> = 0.95f..1.05f,
    val juvenileScale: Float = 0.7f
)

data class PackSpawnParams(
    val level: ServerLevel,
    val centerPos: Vec3,
    val species: String,
    val countRange: IntRange = 6..14,

    // Cấu hình Cấp Độ (Level Hierarchy)
    val leaderLevelRange: IntRange = 36..48,
    val subLeaderLevelDelta: IntRange = 3..6,    // Level Sub-Leader = Leader - (3..6)
    val followerLevelDelta: IntRange = 8..15,    // Level Member = Leader - (8..15)
    val juvenileLevelDelta: IntRange = 18..25,   // Level Juvenile = Leader - (18..25)
    val scaleWithPlayerParty: Boolean = true,

    // Phân cấp & Đội hình
    val hierarchyRule: HierarchySpawnRule = HierarchySpawnRule(),
    val movementType: PackMovementType = PackMovementType.LAND,
    val formation: FormationType = FormationType.SCATTERED,
    val spawnRadius: Double = 10.0,

    // Extra Metadata
    val customTags: List<String> = emptyList(),
    val postSpawnCallback: ((leader: PokemonEntity, pack: List<PokemonEntity>) -> Unit)? = null
)

data class PackSpawnResult(
    val herdId: UUID,
    val leader: PokemonEntity,
    val subLeader: PokemonEntity?,
    val members: List<PokemonEntity>,
    val juveniles: List<PokemonEntity>,
    val totalSpawned: Int
)
