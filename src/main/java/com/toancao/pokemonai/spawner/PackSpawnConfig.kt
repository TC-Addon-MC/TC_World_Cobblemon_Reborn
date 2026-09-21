package com.toancao.pokemonai.spawner

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID

enum class PackMovementType {
    LAND,
    AIR,
    AQUATIC
}

enum class FormationType {
    SCATTERED,
    V_SHAPE,
    PROTECTIVE_CIRCLE,
    COLUMN
}

data class HierarchySpawnRule(
    val hasSubLeader: Boolean = true,
    val subLeaderMinPackSize: Int = 7,
    val juvenileRatio: Float = 0.2f,
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

    val leaderLevelRange: IntRange = 36..48,
    val subLeaderLevelDelta: IntRange = 3..6,
    val followerLevelDelta: IntRange = 8..15,
    val juvenileLevelDelta: IntRange = 18..25,
    val scaleWithPlayerParty: Boolean = true,

    val hierarchyRule: HierarchySpawnRule = HierarchySpawnRule(),
    val movementType: PackMovementType = PackMovementType.LAND,
    val formation: FormationType = FormationType.SCATTERED,
    val spawnRadius: Double = 10.0,

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
