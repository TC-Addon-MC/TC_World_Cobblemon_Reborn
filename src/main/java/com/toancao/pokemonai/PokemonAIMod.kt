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
            
            var nextDay = currentDay
            if (timeOfDay >= 1000 || nextDay == 0L || nextDay % 30 != 0L) {
                val remainder = nextDay % 30
                nextDay += (30 - remainder)
            }
            
            val nextEventTick = (nextDay * 24000) + 1000
            val ticksRemaining = nextEventTick - dayTime
            
            val isIdle = com.toancao.pokemonai.events.DragonGateEvent.currentPhase == com.toancao.pokemonai.events.DragonGateEvent.EventPhase.IDLE
            
            val statusDesc = if (isIdle) {
                "Sự kiện Long Môn chưa diễn ra.\nHãy chờ đến ngày tiếp theo\nvào lúc 7 giờ sáng."
            } else {
                "Sự kiện Long Môn đang diễn ra!\n(Phase: $phaseName)\nHãy nhanh chân đến Cổng Rồng!"
            }
            
            listOf(
                com.toancao.pokemonai.events.NoticeEventManager.NoticeEvent(
                    title = "Sự Kiện Long Môn",
                    subtitle = if (isIdle) "Bắt đầu sau:" else "Kết thúc Phase sau:",
                    desc = statusDesc,
                    remainingTicks = if (isIdle) ticksRemaining else com.toancao.pokemonai.events.DragonGateEvent.phaseTicks.toLong()
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



