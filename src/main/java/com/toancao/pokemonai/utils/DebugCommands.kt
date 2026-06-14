package com.toancao.pokemonai.utils

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import com.toancao.pokemonai.compat.CobblemonBridge

object DebugCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("tcpoke")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("debug")
                        .then(Commands.literal("on").executes {
                            DebugUtils.enabled = true
                            it.source.sendSuccess({ Component.literal("PokemonAI Debug: ON") }, false)
                            1
                        })
                        .then(Commands.literal("off").executes {
                            DebugUtils.enabled = false
                            it.source.sendSuccess({ Component.literal("PokemonAI Debug: OFF") }, false)
                            1
                        })
                )
                .then(
                    Commands.literal("action")
                        .then(
                            Commands.argument("pokemon_name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests { _, builder ->
                                    val names = listOf("all", "magikarp")
                                    names.forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .then(
                                    Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities())
                                        .then(
                                            Commands.argument("actionName", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests { _, builder ->
                                                    val actions = listOf("jump", "evolve")
                                                    actions.forEach { builder.suggest(it) }
                                                    builder.buildFuture()
                                                }
                                                .then(
                                                    Commands.argument("same_y", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                        .executes { context -> executeAction(context, true) }
                                                )
                                                .executes { context -> executeAction(context, false) }
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("event")
                        .then(
                            Commands.literal("dragongate")
                                .then(
                                    Commands.literal("start")
                                        .executes { context ->
                                            com.toancao.pokemonai.events.DragonGateEvent.trigger(context.source.level)
                                            context.source.sendSuccess({ Component.literal("Started Dragon Gate Event manually!") }, true)
                                            1
                                        }
                                )
                                .then(
                                    Commands.literal("stop")
                                        .executes { context ->
                                            com.toancao.pokemonai.events.DragonGateEvent.stop(context.source.level)
                                            context.source.sendSuccess({ Component.literal("Stopped Dragon Gate Event manually!") }, true)
                                            1
                                        }
                                )
                        )
                )
        )
    }

    private fun executeAction(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>, checkY: Boolean): Int {
        val pokemonName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "pokemon_name")
        val targets = net.minecraft.commands.arguments.EntityArgument.getEntities(context, "targets")
        val actionName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "actionName")
        var count = 0
        
        val executor = context.source.entity

        for (entity in targets) {
            if (checkY && executor != null) {
                if (Math.abs(entity.y - executor.y) > 2.0) {
                    continue // Skip if not on same Y level
                }
            }
            if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                val species = CobblemonBridge.getSpeciesName(entity)
                if (pokemonName.equals("all", ignoreCase = true) || species.equals(pokemonName, ignoreCase = true)) {
                    when (actionName.lowercase()) {
                        "jump" -> {
                            com.toancao.pokemonai.behaviors.water.JumpOutOfWaterGoal(entity).start()
                            count++
                        }
                        "evolve" -> {
                            com.toancao.pokemonai.evolution.EvolutionManager.forceEvolve(entity, "gyarados")
                            count++
                        }
                    }
                }
            }
        }
        context.source.sendSuccess({ Component.literal("Executed action '\$actionName' on \$count \$pokemonName(s)") }, true)
        return count
    }
}
