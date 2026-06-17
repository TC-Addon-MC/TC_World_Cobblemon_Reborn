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

        // Load configs
        com.toancao.pokemonai.config.MagikarpConfigManager.loadConfig()

        // Register attachments
        PokemonAttachments.register()

        // Register event handlers & commands
        com.toancao.pokemonai.network.EventNetwork.registerPayloads()
        com.toancao.pokemonai.registry.BlockRegistry.register()
        EmotionEventHandler.register()
        EvolutionManager.register()
        com.toancao.pokemonai.utils.DebugCommands.register()
        com.toancao.pokemonai.events.DragonGateEvent.register()
        
        com.toancao.pokemonai.events.NoticeEventManager.registerProvider { level ->
            val phaseName = com.toancao.pokemonai.events.DragonGateEvent.currentPhase.name
            
            val dayTime = level.dayTime
            val currentDay = dayTime / 24000
            val timeOfDay = dayTime % 24000
            
            val interval = com.toancao.pokemonai.config.MagikarpConfigManager.config.eventIntervalMultiplier * 10
            
            var nextDay = currentDay
            if (timeOfDay >= 1000 || nextDay == 0L || nextDay % interval != 0L) {
                val remainder = nextDay % interval
                nextDay += (interval - remainder)
            }
            
            val nextEventTick = (nextDay * 24000) + 1000
            val ticksRemaining = nextEventTick - dayTime
            
            val isIdle = com.toancao.pokemonai.events.DragonGateEvent.currentPhase == com.toancao.pokemonai.events.DragonGateEvent.EventPhase.IDLE
            
            val statusDesc = if (isIdle) {
                "event.tc_reborn.dragon_gate.idle_desc"
            } else {
                "event.tc_reborn.dragon_gate.active_desc|$phaseName"
            }
            
            listOf(
                com.toancao.pokemonai.events.NoticeEventManager.NoticeEvent(
                    title = "event.tc_reborn.dragon_gate.title",
                    subtitle = if (isIdle) "event.tc_reborn.dragon_gate.subtitle_idle" else "event.tc_reborn.dragon_gate.subtitle_active",
                    desc = statusDesc,
                    remainingTicks = if (isIdle) ticksRemaining else com.toancao.pokemonai.events.DragonGateEvent.phaseTicks.toLong(),
                    icon = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tc_reborn", "textures/gui/magikarp_icon.png"),
                    image = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tc_reborn", "textures/gui/dragon_island.png")
                )
            )
        }

        // Register Magikarp Configs
        BehaviorRegistry.register(MagikarpConfig.species, MagikarpConfig.behaviors)
        EvolutionRegistry.register(MagikarpConfig.species, listOf(
            MagikarpDeterminationRule(),
            MagikarpRageRule()
        ))


        
        logger.info("Pokemon AI Addon Initialized successfully.")
    }
}



