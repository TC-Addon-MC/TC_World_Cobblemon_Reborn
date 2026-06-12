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
                                                    val actions = listOf("jump")
                                                    actions.forEach { builder.suggest(it) }
                                                    builder.buildFuture()
                                                }
                                                .executes { context ->
                                                    val pokemonName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "pokemon_name")
                                                    val targets = net.minecraft.commands.arguments.EntityArgument.getEntities(context, "targets")
                                                    val actionName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "actionName")
                                                    var count = 0
                                                    
                                                    for (entity in targets) {
                                                        if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                                                            val species = CobblemonBridge.getSpeciesName(entity)
                                                            if (pokemonName.equals("all", ignoreCase = true) || species.equals(pokemonName, ignoreCase = true)) {
                                                                when (actionName.lowercase()) {
                                                                    "jump" -> {
                                                                        com.toancao.pokemonai.behaviors.water.JumpOutOfWaterGoal(entity).start()
                                                                        count++
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    context.source.sendSuccess({ Component.literal("Executed action '\$actionName' on \$count \$pokemonName(s)") }, true)
                                                    count
                                                }
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("event")
                        .then(
                            Commands.literal("dragongate")
                                .then(
                                    Commands.literal("add")
                                        .then(
                                            Commands.argument("startX", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(Commands.argument("startY", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(Commands.argument("startZ", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(Commands.argument("endX", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(Commands.argument("endY", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(Commands.argument("endZ", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes { context ->
                                                    val startX = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "startX")
                                                    val startY = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "startY")
                                                    val startZ = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "startZ")
                                                    val endX = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "endX")
                                                    val endY = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "endY")
                                                    val endZ = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "endZ")
                                                    
                                                    val source = context.source
                                                    val dimension = source.level.dimension().location().toString()
                                                    val startPos = net.minecraft.core.BlockPos(startX, startY, startZ)
                                                    val endPos = net.minecraft.core.BlockPos(endX, endY, endZ)
                                                    
                                                    com.toancao.pokemonai.events.DragonGateManager.addGate(dimension, startPos, endPos)
                                                    source.sendSuccess({ Component.literal("Added Dragon Gate in $dimension from ($startX, $startY, $startZ) to ($endX, $endY, $endZ)") }, true)
                                                    1
                                                }
                                            ))))))
                                )
                                .then(
                                    Commands.literal("trigger")
                                        .executes { context ->
                                            com.toancao.pokemonai.events.DragonGateEvent.trigger(context.source.level)
                                            context.source.sendSuccess({ Component.literal("Triggered Dragon Gate Event manually!") }, true)
                                            1
                                        }
                                )
                        )
                )
        )
    }
}
