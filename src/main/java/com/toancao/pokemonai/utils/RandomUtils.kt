package com.toancao.pokemonai.utils

import net.minecraft.world.entity.Entity
import kotlin.random.Random
import net.minecraft.world.phys.Vec3

object RandomUtils {
    fun chance(probability: Float): Boolean {
        return Random.nextFloat() < probability
    }

    fun intRange(min: Int, max: Int): Int {
        if (min >= max) return min
        return Random.nextInt(min, max + 1)
    }

    fun <T> weightedRandom(weights: Map<T, Int>): T? {
        val totalWeight = weights.values.sum()
        if (totalWeight <= 0) return null
        var randomValue = Random.nextInt(totalWeight)
        for ((item, weight) in weights) {
            randomValue -= weight
            if (randomValue < 0) return item
        }
        return null
    }

    enum class Probability(val chance: Float, val description: String) {
        ULTRA_RARE(0.0001f, "Siêu hiếm"),
        RARE(0.0005f, "Hiếm"),
        ULTRA_LOW(0.001f, "Siêu thấp"),
        LOW(0.005f, "Thấp"),
        MODERATE(0.01f, "Vừa"),
        MEDIUM(0.02f, "Trung bình"),
        HIGH(0.05f, "Cao"),
        ULTRA_HIGH(0.1f, "Siêu cao")
    }

    fun chance(prob: Probability): Boolean {
        return chance(prob.chance)
    }
}
