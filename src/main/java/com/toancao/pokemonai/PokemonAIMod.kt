package com.toancao.pokemonai

import com.toancao.pokemonai.attachment.PokemonAttachments
import com.toancao.pokemonai.emotions.EmotionEventHandler
import com.toancao.pokemonai.evolution.EvolutionManager
import com.toancao.pokemonai.evolution.EvolutionRegistry
import com.toancao.pokemonai.evolution.rules.MagikarpDeterminationRule
import com.toancao.pokemonai.evolution.rules.MagikarpRageRule
import com.toancao.pokemonai.pokemon.MagikarpConfig
import com.toancao.pokemonai.registry.BehaviorRegistry
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object PokemonAIMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("pokemonai")

    override fun onInitialize() {
        logger.info("Initializing Pokemon AI Addon...")

        // Register attachments
        PokemonAttachments.register()

        // Register event handlers & commands
        EmotionEventHandler.register()
        EvolutionManager.register()
        com.toancao.pokemonai.utils.DebugCommands.register()
        com.toancao.pokemonai.events.DragonGateEvent.register()

        // Register Magikarp Configs
        BehaviorRegistry.register(MagikarpConfig.species, MagikarpConfig.behaviors)
        EvolutionRegistry.register(MagikarpConfig.species, listOf(
            MagikarpDeterminationRule(),
            MagikarpRageRule()
        ))

        // Example for registering other species (like Feebas, Goldeen)
        // BehaviorRegistry.register("feebas", listOf(...))
        // EvolutionRegistry.register("feebas", listOf(...))
        
        logger.info("Pokemon AI Addon Initialized successfully.")
    }
}



