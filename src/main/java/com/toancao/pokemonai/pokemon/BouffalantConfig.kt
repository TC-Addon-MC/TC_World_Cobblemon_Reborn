package com.toancao.pokemonai.pokemon

import com.toancao.pokemonai.behaviors.combat.BullRushChargeGoal
import com.toancao.pokemonai.behaviors.combat.HerdSharedAggroGoal
import com.toancao.pokemonai.behaviors.herd.*
import com.toancao.pokemonai.config.HerdConfigManager
import com.toancao.pokemonai.registry.BehaviorRegistry
import com.toancao.pokemonai.spawner.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object BouffalantConfig {
    const val SPECIES = "bouffalant"

    fun createPackParams(level: ServerLevel, centerPos: Vec3, customCount: Int? = null): PackSpawnParams {
        val cfg = HerdConfigManager.config.bouffalant
        val countRange = if (customCount != null) customCount..customCount else cfg.minPackSize..cfg.maxPackSize

        return PackSpawnParams(
            level = level,
            centerPos = centerPos,
            species = SPECIES,
            countRange = countRange,
            leaderLevelRange = cfg.minLeaderLevel..cfg.maxLeaderLevel,
            subLeaderLevelDelta = 3..6,
            followerLevelDelta = 8..15,
            juvenileLevelDelta = 20..26,
            hierarchyRule = HierarchySpawnRule(
                hasSubLeader = false,
                subLeaderMinPackSize = 999,
                juvenileRatio = 0.0f,
                leaderScale = cfg.leaderScale,
                subLeaderScale = 1.1f,
                memberScaleRange = cfg.memberScaleMin..cfg.memberScaleMax,
                juvenileScale = 0.7f
            ),
            movementType = PackMovementType.LAND,
            formation = FormationType.SCATTERED,
            spawnRadius = 12.0,
            customTags = listOf("tc_bouffalant_herd")
        )
    }

    val behaviors: List<BehaviorRegistry.Entry> = listOf(
        BehaviorRegistry.Entry(1) { entity -> HerdStampedeGoal(entity) },
        BehaviorRegistry.Entry(2) { entity -> BullRushChargeGoal(entity) },
        BehaviorRegistry.Entry(3) { entity -> HerdSharedAggroGoal(entity) },
        BehaviorRegistry.Entry(4) { entity -> HerdLeadershipSuccessionGoal(entity) },
        BehaviorRegistry.Entry(5) { entity -> FollowHerdLeaderGoal(entity) },
        BehaviorRegistry.Entry(6) { entity -> HornClashGoal(entity) }
    )
}
