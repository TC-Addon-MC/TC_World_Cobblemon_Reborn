package com.toancao.pokemonai.pokemon

import com.toancao.pokemonai.behaviors.water.DragonGateChallengeGoal
import com.toancao.pokemonai.behaviors.water.JumpOutOfWaterGoal
import com.toancao.pokemonai.behaviors.water.SwimUpwardGoal
import com.toancao.pokemonai.registry.BehaviorRegistry

object MagikarpConfig {
    val species = "magikarp"
    
    val behaviors = listOf(
        BehaviorRegistry.Entry(2, { entity -> com.toancao.pokemonai.behaviors.water.WeakSwimmerGoal(entity) }),
        BehaviorRegistry.Entry(3, { entity -> JumpOutOfWaterGoal(entity) }),
        BehaviorRegistry.Entry(4, { entity -> SwimUpwardGoal(entity) }),
        BehaviorRegistry.Entry(5, { entity -> DragonGateChallengeGoal(entity) }),
        BehaviorRegistry.Entry(6, { entity -> com.toancao.pokemonai.behaviors.water.DragonGateFreeSwimGoal(entity) })
    )

    // A data class for profile config if needed, here just defined locally
    data class EmotionProfile(val baseRage: Int, val baseDetermination: Int, val baseFear: Int)
    
    val emotionProfile = EmotionProfile(
        baseRage = 0, baseDetermination = 20, baseFear = 30
    )
}



