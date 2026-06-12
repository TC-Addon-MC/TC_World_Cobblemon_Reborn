@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.utils

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.evolution.EvolutionRule
import com.toancao.pokemonai.evolution.EvolutionResult
import com.toancao.pokemonai.emotions.EmotionType
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import org.joml.Vector3f
import org.slf4j.LoggerFactory

object DebugUtils {
    var enabled = true
    private val logger = LoggerFactory.getLogger("PokemonAI-Debug")

    fun logEmotion(entity: Entity, pokemon: PokemonEntity) {
        if (!enabled) return
        val emotion = EntityUtils.getEmotion(entity)
        logger.info("[Emotion] ${CobblemonBridge.getSpeciesName(pokemon)}: Rage=${emotion.rage}, Det=${emotion.determination}, Fear=${emotion.fear}")
    }

    fun logEvolutionCheck(entity: Entity, pokemon: PokemonEntity, rule: EvolutionRule, result: EvolutionResult?) {
        if (!enabled) return
        logger.info("[Evolution] Checked ${rule.javaClass.simpleName} for ${CobblemonBridge.getSpeciesName(pokemon)}. Result: ${result?.targetSpecies ?: "None"}")
    }

    fun logAttachment(entity: Entity, pokemon: PokemonEntity) {
        if (!enabled) return
        val emotion = EntityUtils.getEmotion(entity)
        val state = EntityUtils.getEvolutionState(entity)
        logger.info("[Attachment] Emotion: $emotion | State: $state")
    }

    fun spawnDebugParticle(entity: Entity, type: EmotionType) {
        if (!enabled) return
        val world = entity.level() as? ServerLevel ?: return
        val color = when (type) {
            EmotionType.RAGE -> Vector3f(1.0f, 0.0f, 0.0f)
            EmotionType.DETERMINATION -> Vector3f(1.0f, 1.0f, 0.0f)
            EmotionType.FEAR -> Vector3f(0.0f, 0.0f, 1.0f)
            else -> Vector3f(1.0f, 1.0f, 1.0f)
        }
        val particle = DustParticleOptions(color, 1.0f)
        world.sendParticles(particle, entity.x, entity.y + 1.0, entity.z, 5, 0.2, 0.2, 0.2, 0.0)
    }

    fun sendDebugActionBar(player: ServerPlayer, entity: Entity, pokemon: PokemonEntity) {
        if (!enabled) return
        val emotion = EntityUtils.getEmotion(entity)
        player.sendSystemMessage(Component.literal("Target: ${CobblemonBridge.getSpeciesName(pokemon)} | R:${emotion.rage} D:${emotion.determination} F:${emotion.fear}"))
    }

    fun dumpRegistries() {
        if (!enabled) return
        logger.info("[Dump] Registry dump executed.")
    }

    fun logAction(entity: PokemonEntity, action: String, details: String) {
        if (!enabled) return
        val species = CobblemonBridge.getSpeciesName(entity)
        val mcEntity = entity as net.minecraft.world.entity.Entity
        val formatX = String.format("%.1f", mcEntity.x)
        val formatY = String.format("%.1f", mcEntity.y)
        val formatZ = String.format("%.1f", mcEntity.z)
        logger.info("[Action] $species at ($formatX, $formatY, $formatZ): $action - $details")
    }

    fun logEvent(message: String) {
        if (!enabled) return
        logger.info("[Event] $message")
    }
}
