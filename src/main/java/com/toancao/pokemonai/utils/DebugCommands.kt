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
                            it.source.sendSuccess({ Component.translatable("command.tc_reborn.debug.on") }, false)
                            1
                        })
                        .then(Commands.literal("off").executes {
                            DebugUtils.enabled = false
                            it.source.sendSuccess({ Component.translatable("command.tc_reborn.debug.off") }, false)
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
                                                    val actions = listOf("jump", "evolve", "circle")
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
                                            context.source.sendSuccess({ Component.translatable("command.tc_reborn.dragongate.started") }, true)
                                            1
                                        }
                                )
                                .then(
                                    Commands.literal("stop")
                                        .executes { context ->
                                            com.toancao.pokemonai.events.DragonGateEvent.stop(context.source.level)
                                            context.source.sendSuccess({ Component.translatable("command.tc_reborn.dragongate.stopped") }, true)
                                            1
                                        }
                                )
                        )
                )
                .then(
                    Commands.literal("fly")
                        .then(
                            Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests { _, builder ->
                                    com.toancao.pokemonai.flight.CustomFlightRegistry.getAllSpeciesNames()
                                        .forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .then(
                                    Commands.argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                        .then(
                                            Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests { _, builder ->
                                                    builder.suggest("hover")
                                                    builder.suggest("land")
                                                    builder.buildFuture()
                                                }
                                                .executes { context ->
                                                    val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                                                    val pos = net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context, "pos")
                                                    val action = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action")
                                                    executeFly(context, species, pos, action)
                                                }
                                        )
                                )
                                .then(Commands.literal("takeoff")
                                    .then(Commands.argument("altitude", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                                        .executes { executeForceAction(it, "takeoff_alt") })
                                    .executes { executeForceAction(it, "takeoff") })
                                .then(Commands.literal("land").executes { executeForceAction(it, "land") })
                                .then(Commands.literal("stop").executes { executeForceAction(it, "stop") })
                                .then(Commands.literal("hover").executes { executeForceAction(it, "hover") })
                                .then(Commands.literal("up")
                                    .then(Commands.argument("blocks", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                                        .executes { executeForceAction(it, "up_blocks") })
                                    .executes { executeForceAction(it, "up") })
                                .then(Commands.literal("down")
                                    .then(Commands.argument("blocks", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                                        .executes { executeForceAction(it, "down_blocks") })
                                    .executes { executeForceAction(it, "down") })
                        )
                )
                .then(
                    Commands.literal("flycancel")
                        .then(
                            Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests { _, builder ->
                                    com.toancao.pokemonai.flight.CustomFlightRegistry.getAllSpeciesNames()
                                        .forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .executes { context ->
                                    val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                                    executeFlyCancel(context, species)
                                }
                        )
                )
        )
    }

    private fun executeFly(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        species: String,
        pos: net.minecraft.world.phys.Vec3,
        action: String
    ): Int {
        val source = context.source
        val level = source.level
        val executor = source.entity

        val pokemon = findNearestPokemonOfSpecies(level, executor, species, 128.0)
            ?: run {
                source.sendFailure(Component.translatable("command.tc_reborn.fly.not_found", species))
                return 0
            }

        val hover = action.equals("hover", ignoreCase = true)
        val actionText = if (hover) Component.translatable("command.tc_reborn.fly.action.hover").string else Component.translatable("command.tc_reborn.fly.action.land").string

        com.toancao.pokemonai.flight.engine.FlightEngine.flyTo(pokemon, pos, hover)
        
        source.sendSuccess({ Component.translatable(
            "command.tc_reborn.fly.normal", species, String.format("%.1f", pos.x), String.format("%.1f", pos.y), String.format("%.1f", pos.z), actionText
        ) }, true)
        
        return 1
    }

    private fun executeFlyCancel(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        species: String
    ): Int {
        val source = context.source
        val level = source.level
        val executor = source.entity

        val pokemon = findNearestPokemonOfSpecies(level, executor, species, 128.0)
            ?: run {
                source.sendFailure(Component.translatable("command.tc_reborn.fly.not_found", species))
                return 0
            }

        com.toancao.pokemonai.flight.engine.FlightEngine.stopFlight(pokemon)
        source.sendSuccess({ Component.translatable("command.tc_reborn.fly.cancelled", species) }, true)
        return 1
    }

    private fun executeForceAction(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        action: String
    ): Int {
        val source = context.source
        val level = source.level
        val executor = source.entity
        val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")

        val pokemon = findNearestPokemonOfSpecies(level, executor, species, 128.0)
            ?: run {
                source.sendFailure(Component.translatable("command.tc_reborn.fly.not_found", species))
                return 0
            }

        val mob = pokemon as? net.minecraft.world.entity.Mob
        val engine = com.toancao.pokemonai.flight.engine.FlightEngine
        when (action) {
            "takeoff" -> {
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, (mob?.y ?: 0.0) + 10.0, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.takeoff_default", species) }, true)
            }
            "takeoff_alt" -> {
                val alt = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "altitude")
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, (mob?.y ?: 0.0) + alt, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.takeoff", species, alt) }, true)
            }
            "land" -> {
                // Sử dụng hàm land thông minh thay vì stopFlight đột ngột
                engine.land(pokemon)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.force_land", species) }, true)
            }
            "stop" -> {
                engine.stopFlight(pokemon)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.cancelled", species) }, true)
            }
            "hover" -> {
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, mob?.y ?: 0.0, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.force_hover", species) }, true)
            }
            "up" -> {
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, (mob?.y ?: 0.0) + 5.0, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.up_default", species) }, true)
            }
            "up_blocks" -> {
                val blocks = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "blocks")
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, (mob?.y ?: 0.0) + blocks, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.up", species, blocks) }, true)
            }
            "down" -> {
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, (mob?.y ?: 0.0) - 5.0, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.down_default", species) }, true)
            }
            "down_blocks" -> {
                val blocks = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "blocks")
                val targetPos = net.minecraft.world.phys.Vec3(mob?.x ?: 0.0, (mob?.y ?: 0.0) - blocks, mob?.z ?: 0.0)
                engine.flyTo(pokemon, targetPos, hover = true)
                source.sendSuccess({ Component.translatable("command.tc_reborn.fly.down", species, blocks) }, true)
            }
        }
        return 1
    }

    /**
     * Tìm PokemonEntity gần nhất khớp tên loài trong phạm vi radius.
     * Ưu tiên Pokemon gần executor nhất nếu executor là entity, không thì gần tọa độ 0,0,0.
     */
    private fun findNearestPokemonOfSpecies(
        level: net.minecraft.server.level.ServerLevel,
        executor: net.minecraft.world.entity.Entity?,
        speciesName: String,
        radius: Double
    ): com.cobblemon.mod.common.entity.pokemon.PokemonEntity? {
        val cx = executor?.x ?: 0.0
        val cy = executor?.y ?: 64.0
        val cz = executor?.z ?: 0.0

        return level.getEntitiesOfClass(
            com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java,
            net.minecraft.world.phys.AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius)
        ) { entity ->
            CobblemonBridge.getSpeciesName(entity).equals(speciesName, ignoreCase = true)
        }.minByOrNull { it.distanceTo(executor ?: it) }
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
                        "circle" -> {
                            val machine = com.toancao.pokemonai.flight.CustomFlightManager.getMachine(entity.uuid)
                            if (machine != null) {
                                machine.transitionTo(com.toancao.pokemonai.flight.FlightState.CIRCULAR_FLYING)
                            }
                            count++
                        }
                    }
                }
            }
        }
        context.source.sendSuccess({ Component.translatable("command.tc_reborn.action.executed", actionName, count, pokemonName) }, true)
        return count
    }
}
