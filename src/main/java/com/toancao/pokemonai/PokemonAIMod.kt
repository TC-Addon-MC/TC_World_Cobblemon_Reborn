package com.toancao.pokemonai

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.toancao.pokemonai.attachment.PokemonAttachments
import com.toancao.pokemonai.behaviors.BehaviorApplicator
import com.toancao.pokemonai.emotions.EmotionEventHandler
import com.toancao.pokemonai.evolution.EvolutionManager
import com.toancao.pokemonai.evolution.EvolutionRegistry
import com.toancao.pokemonai.evolution.rules.MagikarpDeterminationRule
import com.toancao.pokemonai.evolution.rules.MagikarpRageRule
import com.toancao.pokemonai.pokemon.MagikarpConfig
import com.toancao.pokemonai.registry.BehaviorRegistry
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

object PokemonAIMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("pokemonai")
    private var cobblemonIntegrationsRegistered = false

    override fun onInitialize() {
        logger.info("Initializing Pokemon AI Addon...")

        com.toancao.pokemonai.config.MagikarpConfigManager.loadConfig()
        com.toancao.pokemonai.config.HerdConfigManager.loadConfig()
        com.toancao.pokemonai.config.AronConfigManager.loadConfig()

        PokemonAttachments.register()
        com.toancao.pokemonai.network.EventNetwork.registerPayloads()
        com.toancao.pokemonai.registry.BlockRegistry.register()
        com.toancao.pokemonai.utils.DebugCommands.register()
        com.toancao.pokemonai.events.DragonGateEvent.register()
        registerNoticeProvider()

        CobblemonEvents.COBBLEMON_INITIALISED.subscribe {
            registerCobblemonIntegrations()
        }
        registerCobblemonIntegrations()

        logger.info("Pokemon AI Addon initialized.")
    }

    private fun registerNoticeProvider() {
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
                    icon = ResourceLocation.fromNamespaceAndPath("tc_reborn", "textures/gui/magikarp_icon.png"),
                    image = ResourceLocation.fromNamespaceAndPath("tc_reborn", "textures/gui/dragon_island.png")
                )
            )
        }
    }

    private fun registerCobblemonIntegrations() {
        if (cobblemonIntegrationsRegistered) {
            logger.debug("Cobblemon integrations already registered; skipping duplicate lifecycle callback.")
            return
        }
        cobblemonIntegrationsRegistered = true

        logger.info("Registering Pokemon AI Cobblemon integrations...")

        EmotionEventHandler.register()
        EvolutionManager.register()
        registerFlightIntegration()
        registerPokemonConfigs()

        BehaviorApplicator.register()
        com.toancao.pokemonai.spawner.hooks.CobblemonNaturalSpawnHook.register()
        com.toancao.pokemonai.behaviors.herd.HerdAggroEventHandler.register()

        logger.info("Pokemon AI Cobblemon integrations registered successfully.")
    }

    private fun registerFlightIntegration() {
        if (FabricLoader.getInstance().isModLoaded("flyingspawn")) {
            logger.error("========== MOD CONFLICT WARNING ==========")
            logger.error("Detected legacy mod TC_Cobble_Flight (flyingspawn) is still installed!")
            logger.error("The new flight system in TC_world_reborn will be TEMPORARILY DISABLED to yield control to the old mod.")
            logger.error("Please remove the TC_Cobble_Flight jar from your mods folder to use the latest flight system!")
            logger.error("==========================================")
            return
        }

        com.toancao.pokemonai.flight.CustomFlightInit.registerDefaultFlyingPokemon()
        com.toancao.pokemonai.flight.CustomFlightManager.register()
    }

    private fun registerPokemonConfigs() {
        BehaviorRegistry.register(MagikarpConfig.species, MagikarpConfig.behaviors)
        EvolutionRegistry.register(
            MagikarpConfig.species,
            listOf(
                MagikarpDeterminationRule(),
                MagikarpRageRule()
            )
        )

        BehaviorRegistry.register(com.toancao.pokemonai.pokemon.TaurosConfig.SPECIES, com.toancao.pokemonai.pokemon.TaurosConfig.behaviors)
        com.toancao.pokemonai.spawner.PackSpawnRegistry.register(com.toancao.pokemonai.pokemon.TaurosConfig.SPECIES) { level, pos ->
            com.toancao.pokemonai.pokemon.TaurosConfig.createPackParams(level, pos)
        }

        BehaviorRegistry.register(com.toancao.pokemonai.pokemon.BouffalantConfig.SPECIES, com.toancao.pokemonai.pokemon.BouffalantConfig.behaviors)
        com.toancao.pokemonai.spawner.PackSpawnRegistry.register(com.toancao.pokemonai.pokemon.BouffalantConfig.SPECIES) { level, pos ->
            com.toancao.pokemonai.pokemon.BouffalantConfig.createPackParams(level, pos)
        }

        BehaviorRegistry.register(com.toancao.pokemonai.pokemon.AronConfig.SPECIES, com.toancao.pokemonai.pokemon.AronConfig.behaviors)
    }
}
