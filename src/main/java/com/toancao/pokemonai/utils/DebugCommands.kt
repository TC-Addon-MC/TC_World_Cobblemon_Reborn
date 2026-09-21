package com.toancao.pokemonai.utils

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.spawner.hierarchy.getHerdData
import com.toancao.pokemonai.spawner.hierarchy.setHerdData
import com.toancao.pokemonai.spawner.hierarchy.HerdRole
import com.toancao.pokemonai.spawner.GenericPackSpawner
import com.toancao.pokemonai.spawner.PackSpawnRegistry
import com.toancao.pokemonai.pokemon.TaurosConfig
import com.cobblemon.mod.common.pokemon.PokemonSizeCategory

object DebugCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("tcpoke").requires { it.hasPermission(2) }

        registerDebugCommand(root)
        registerActionCommand(root)
        registerEventCommand(root)
        registerFlyCommand(root)
        registerFlyCancelCommand(root)
        registerSpawnAirCommand(root)
        registerPackCommand(root)
        registerFlightDiagCommand(root)
        registerFlyNativeCommand(root)
        registerNativeToggleCommand(root)
        registerTestFlyCommand(root)

        dispatcher.register(root)
    }

    private fun registerDebugCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
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
    }

    private fun registerActionCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("action")
                .then(
                    Commands.argument("pokemon_name", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests { _, builder ->
                            listOf("all", "magikarp").forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .then(
                            Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities())
                                .then(
                                    Commands.argument("actionName", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests { _, builder ->
                                            listOf("jump", "evolve", "circle").forEach { builder.suggest(it) }
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
    }

    private fun registerEventCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("event")
                .then(
                    Commands.literal("dragongate")
                        .then(Commands.literal("start").executes { context ->
                            com.toancao.pokemonai.events.DragonGateEvent.trigger(context.source.level)
                            context.source.sendSuccess({ Component.translatable("command.tc_reborn.dragongate.started") }, true)
                            1
                        })
                        .then(Commands.literal("stop").executes { context ->
                            com.toancao.pokemonai.events.DragonGateEvent.stop(context.source.level)
                            context.source.sendSuccess({ Component.translatable("command.tc_reborn.dragongate.stopped") }, true)
                            1
                        })
                )
        )
    }

    private fun registerFlyCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
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
    }

    private fun registerFlyCancelCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
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
    }

    private fun registerSpawnAirCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("spawnair")
                .executes { context -> executeSpawnAir(context) }
        )
    }

    /**
     * Phase 8: quan sát runtime flight. Chỉ dùng khi debug, không ảnh hưởng gameplay.
     * /tcpoke flightdiag — in số machine/session.
     * /tcpoke flightdiag watersurface <species> — in mặt nước thật tại Pokémon.
     * /tcpoke flightdiag landingsite <species> — in điểm đáp được chọn.
     */
    private fun registerFlightDiagCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("flightdiag")
                .executes { context ->
                    val machines = com.toancao.pokemonai.flight.CustomFlightManager.machineCount()
                    val sessions = com.toancao.pokemonai.flight.engine.FlightEngine.sessionCount()
                    context.source.sendSuccess(
                        { Component.literal("Flight machines=$machines sessions=$sessions") },
                        false
                    )
                    machines
                }
                .then(
                    Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(
                            Commands.literal("watersurface").executes { context ->
                                val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                                val pokemon = findNearestPokemonOfSpecies(context.source.level, context.source.entity, species, 128.0)
                                    ?: run {
                                        context.source.sendFailure(Component.literal("Không tìm thấy $species"))
                                        return@executes 0
                                    }
                                val mob = pokemon as net.minecraft.world.entity.Mob
                                val surface = com.toancao.pokemonai.flight.FlightHelpers.findWaterSurfaceY(mob)
                                context.source.sendSuccess(
                                    { Component.literal("WaterSurface($species) = ${surface?.let { String.format("%.2f", it) } ?: "null"} at y=${String.format("%.2f", mob.y)}") },
                                    false
                                )
                                1
                            }
                        )
                        .then(
                            Commands.literal("landingsite").executes { context ->
                                val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                                val pokemon = findNearestPokemonOfSpecies(context.source.level, context.source.entity, species, 128.0)
                                    ?: run {
                                        context.source.sendFailure(Component.literal("Không tìm thấy $species"))
                                        return@executes 0
                                    }
                                val site = try {
                                    com.toancao.pokemonai.flight.navigation.LandingSiteFinder.findLandingSite(pokemon)
                                } catch (e: Exception) {
                                    null
                                }
                                context.source.sendSuccess(
                                    { Component.literal("LandingSite($species) = ${site?.let { String.format("(%.1f, %.1f, %.1f)", it.x, it.y, it.z) } ?: "null"}") },
                                    false
                                )
                                1
                            }
                        )
                )
        )
    }

    /**
     * `/tcpoke testfly [species]` — dựng sân bay thử nghiệm trước mặt người chơi
     * (sân cất cánh, tường 5x5, tán lá, hồ nông, mái đáp, hồ sâu kính + thả chim test).
     * `/tcpoke testfly clear` — khôi phục block + dọn chim test.
     */
    private fun registerTestFlyCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("testfly")
                .executes { context -> executeTestFly(context, "pidgeot") }
                .then(
                    Commands.literal("clear").executes { context ->
                        val level = context.source.level
                        val restored = TestFlyRig.clear(level)
                        context.source.sendSuccess(
                            { Component.literal("Đã dọn sân testfly ($restored block khôi phục, chim test đã xóa)") },
                            true
                        )
                        restored
                    }
                )
                .then(
                    Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests { _, builder ->
                            com.toancao.pokemonai.flight.CustomFlightRegistry.getAllSpeciesNames()
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { context ->
                            val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                            executeTestFly(context, species)
                        }
                )
        )
    }

    private fun executeTestFly(
        context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>,
        species: String
    ): Int {
        val player = context.source.player
        if (player == null) {
            context.source.sendFailure(Component.literal("Lệnh này chỉ dùng được cho người chơi!"))
            return 0
        }
        return try {
            val report = TestFlyRig.build(player.serverLevel(), player, species)
            if (report.birdSpawned == null) {
                context.source.sendSuccess(
                    { Component.literal("Đã dựng sân testfly tại [${report.center.x}, ${report.center.y}, ${report.center.z}] (${report.blocksPlaced} block) nhưng KHÔNG thả được chim '$species' (sai tên hoặc loài chưa có flight preset)") },
                    true
                )
                return 1
            }
            context.source.sendSuccess(
                {
                    Component.literal(
                        "Đã dựng sân testfly tại [${report.center.x}, ${report.center.y}, ${report.center.z}] " +
                            "(${report.blocksPlaced} block) + thả ${report.birdSpawned}. " +
                            "Dùng /tcpoke debug on để xem state, /tcpoke nativenav ${report.birdSpawned} on để test native, /tcpoke testfly clear để dọn."
                    )
                },
                true
            )
            1
        } catch (e: IllegalStateException) {
            context.source.sendFailure(Component.literal("Dựng sân thất bại: ${e.message}"))
            0
        } catch (_: Exception) {
            context.source.sendFailure(Component.literal("Dựng sân thất bại do lỗi không xác định"))
            0
        }
    }
    private fun registerNativeToggleCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("nativenav")
                .then(
                    Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(
                            Commands.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests { _, builder ->
                                    builder.suggest("on")
                                    builder.suggest("off")
                                    builder.buildFuture()
                                }
                                .executes { context ->
                                    val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                                    val state = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "state")
                                    val enabled = state.equals("on", ignoreCase = true)
                                    if (!enabled && !state.equals("off", ignoreCase = true)) {
                                        context.source.sendFailure(Component.literal("Dùng on hoặc off"))
                                        return@executes 0
                                    }
                                    val count = com.toancao.pokemonai.flight.CustomFlightManager.setNativeForSpecies(species, enabled)
                                    context.source.sendSuccess(
                                        { Component.literal("Native navigation $species -> ${if (enabled) "ON" else "OFF"} ($count machine)") },
                                        true
                                    )
                                    count
                                }
                        )
                )
        )
    }
    private fun registerFlyNativeCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("flynative")
                .then(
                    Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(
                            Commands.argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                .executes { context ->
                                    val species = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "species")
                                    val pos = net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context, "pos")
                                    val pokemon = findNearestPokemonOfSpecies(context.source.level, context.source.entity, species, 128.0)
                                        ?: run {
                                            context.source.sendFailure(Component.translatable("command.tc_reborn.fly.not_found", species))
                                            return@executes 0
                                        }
                                    com.toancao.pokemonai.flight.engine.FlightEngine.stopFlight(pokemon)
                                    val ok = com.toancao.pokemonai.flight.navigation.NativeFlightMovement.moveTo(pokemon, pos, speed = 1.0)
                                    if (ok) {
                                        context.source.sendSuccess(
                                            { Component.literal("Native fly $species -> (${String.format("%.1f", pos.x)}, ${String.format("%.1f", pos.y)}, ${String.format("%.1f", pos.z)})") },
                                            true
                                        )
                                        1
                                    } else {
                                        context.source.sendFailure(Component.literal("Native fly thất bại (loài không biết bay hoặc navigation từ chối)"))
                                        0
                                    }
                                }
                        )
                )
        )
    }

    private fun executeSpawnAir(context: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Int {
        val player = context.source.player
        if (player == null) {
            context.source.sendFailure(Component.literal("Lệnh này chỉ dùng được cho người chơi!"))
            return 0
        }

        val success = com.toancao.pokemonai.flight.spawner.CustomAirSpawner.trySpawnNearPlayer(player)
        if (success) {
            context.source.sendSuccess({ Component.literal("Đã kích hoạt Air Spawn thành công (tạo CloudBlock và spawn Pokemon)!") }, true)
            return 1
        } else {
            context.source.sendFailure(Component.literal("Air Spawn thất bại (Không có pokemon phù hợp môi trường hoặc xịt rate)"))
            return 0
        }
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
                    continue
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

    private fun registerPackCommand(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("pack")
                .then(
                    Commands.literal("spawn")
                        .then(
                            Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests { _, builder ->
                                    builder.suggest("tauros")
                                    builder.suggest("bouffalant")
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    val species = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "species")
                                    executePackSpawn(ctx.source, species, null)
                                }
                                .then(
                                    Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 30))
                                        .executes { ctx ->
                                            val species = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "species")
                                            val count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count")
                                            executePackSpawn(ctx.source, species, count)
                                        }
                                )
                        )
                )
                .then(
                    Commands.literal("info").executes { ctx ->
                        executePackInfo(ctx.source)
                    }
                )
                .then(
                    Commands.literal("list").executes { ctx ->
                        executePackList(ctx.source)
                    }
                )
                .then(
                    Commands.literal("leave").executes { ctx ->
                        executePackLeave(ctx.source)
                    }
                )
                .then(
                    Commands.literal("join").executes { ctx ->
                        executePackJoin(ctx.source)
                    }
                )
                .then(
                    Commands.literal("toggle").executes { ctx ->
                        executePackToggle(ctx.source)
                    }
                )
                .then(
                    Commands.literal("names").executes { ctx ->
                        executePackNames(ctx.source)
                    }
                )
                .then(
                    Commands.literal("test")
                        .then(Commands.literal("clash").executes { executeTestClash(it.source) })
                        .then(Commands.literal("charge").executes { executeTestCharge(it.source) })
                        .then(Commands.literal("kill_leader").executes { executeTestKillLeader(it.source) })
                        .then(Commands.literal("aggro").executes { executeTestAggro(it.source) })
                )
        )
    }

    private fun executePackSpawn(source: CommandSourceStack, species: String, count: Int?): Int {
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cChỉ người chơi mới có thể dùng lệnh này!"))
            return 0
        }
        val level = player.serverLevel()
        val factory = com.toancao.pokemonai.spawner.PackSpawnRegistry.getFactory(species)
        if (factory == null) {
            source.sendFailure(Component.literal("§cLoài '$species' chưa được đăng ký trong PackSpawnRegistry!"))
            return 0
        }

        val params = if (count != null && species.equals("tauros", ignoreCase = true)) {
            com.toancao.pokemonai.pokemon.TaurosConfig.createPackParams(level, player.position(), count)
        } else if (count != null && species.equals("bouffalant", ignoreCase = true)) {
            com.toancao.pokemonai.pokemon.BouffalantConfig.createPackParams(level, player.position(), count)
        } else {
            factory(level, player.position())
        }

        val result = com.toancao.pokemonai.spawner.GenericPackSpawner.spawnPokemonPack(params)
        if (result != null) {
            source.sendSuccess({
                Component.literal("§a[Pack] Sinh đàn §e$species§a thành công! Tổng số: §e${result.totalSpawned}§a con (Herd ID: §7${result.herdId}§a)")
            }, true)
            return 1
        } else {
            source.sendFailure(Component.literal("§cKhông thể sinh đàn (có thể do chạm trần chunk limit hoặc vị trí không hợp lệ)."))
            return 0
        }
    }

    private fun executePackInfo(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val eyePos = player.eyePosition
        val lookVec = player.lookAngle.scale(8.0)
        val searchBox = player.boundingBox.expandTowards(lookVec).inflate(2.0)

        val target = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, searchBox) {
            it != player
        }.minByOrNull { it.distanceToSqr(player) }

        if (target == null) {
            source.sendFailure(Component.literal("§cKhông tìm thấy Pokémon nào trong tầm nhìn (phạm vi 8 block)."))
            return 0
        }

        val herdData = target.getHerdData()
        val species = CobblemonBridge.getSpeciesName(target)
        val levelVal = target.pokemon.level
        val scaleVal = target.attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)?.value ?: 1.0
        val nativeAlpha = target.pokemon.isAlpha
        val sizeCategory = PokemonSizeCategory.fromScale(target.pokemon.scaleModifier)
        val herdSource = if (herdData.isNativeHerd) "Cobblemon 1.8" else "TC"
        val nativeHerdSize = if (herdData.isNativeHerd) target.getHerdSize().toString() else "N/A"

        source.sendSuccess({
            Component.literal("§6========== THÔNG TIN BẦY ĐÀN ==========\n" +
                    "§eLoài: §f$species (Lv $levelVal)\n" +
                    "§eScale: §f${String.format("%.2f", scaleVal)}x\n" +
                    "§eVai trò: §a${if (herdData.isLeader) "👑 ĐẦU ĐÀN (Leader)" else "🐂 ĐÀN EM (Member)"}\n" +
                    "§eNative Alpha: §f$nativeAlpha\n" +
                    "§eSize Category: §f$sizeCategory\n" +
                    "§eHerd Source: §f$herdSource\n" +
                    "§eNative Herd Size: §f$nativeHerdSize\n" +
                    "§eHerd ID: §7${herdData.herdId ?: "Không có (Đơn lẻ)"}\n" +
                    "§eLeader UUID: §7${herdData.leaderUUID ?: "None"}\n" +
                    "§eFormation Index: §f${herdData.formationIndex}\n" +
                    "§eIs Battling: §f${target.isBattling}\n" +
                    "§6========================================")
        }, false)
        return 1
    }

    private fun executePackList(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(player.position(), 128.0, 64.0, 128.0)

        val pokemonList = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            it.getHerdData().isInHerd
        }

        val grouped = pokemonList.groupBy { it.getHerdData().herdId }
        if (grouped.isEmpty()) {
            source.sendSuccess({ Component.literal("§eKhông có bầy đàn nào đang hoạt động trong bán kính 64 block.") }, false)
            return 0
        }

        source.sendSuccess({
            val sb = StringBuilder("§6=== DANH SÁCH BẦY ĐÀN TRONG 64 BLOCK (${grouped.size} đàn) ===\n")
            for ((herdId, members) in grouped) {
                val leader = members.firstOrNull { it.getHerdData().isLeader }
                val leaderPosStr = leader?.let { "[${it.blockX}, ${it.blockY}, ${it.blockZ}]" } ?: "Mất tích"
                sb.append("§e- Herd §7${herdId?.toString()?.substring(0, 8)}...§e: §f${members.size} con §a(Thủ lĩnh tại: $leaderPosStr)\n")
            }
            Component.literal(sb.toString().trimEnd())
        }, false)
        return grouped.size
    }

    private fun executeTestClash(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(player.position(), 24.0, 12.0, 24.0)

        val taurosList = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            CobblemonBridge.getSpeciesName(it) == "tauros"
        }

        if (taurosList.size < 2) {
            source.sendFailure(Component.literal("§cCần ít nhất 2 con Tauros ở gần (bán kính 12 block) để test đấu sừng!"))
            return 0
        }

        val first = taurosList[0]
        val second = taurosList[1]
        first.lookControl.setLookAt(second, 30f, 30f)
        second.lookControl.setLookAt(first, 30f, 30f)

        source.sendSuccess({ Component.literal("§aĐã kích hoạt ép đấu sừng giữa 2 con Tauros gần nhất!") }, true)
        return 1
    }

    private fun executeTestCharge(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(player.position(), 24.0, 12.0, 24.0)

        val tauros = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            CobblemonBridge.getSpeciesName(it) == "tauros"
        }.minByOrNull { it.distanceToSqr(player) }

        if (tauros == null) {
            source.sendFailure(Component.literal("§cKhông tìm thấy con Tauros nào trong bán kính 12 block để test húc!"))
            return 0
        }

        tauros.target = player
        source.sendSuccess({ Component.literal("§aĐã kích hoạt Tauros lấy đà lao húc về phía bạn!") }, true)
        return 1
    }

    private fun executeTestKillLeader(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(player.position(), 32.0, 16.0, 32.0)

        val leader = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            it.getHerdData().isLeader
        }.minByOrNull { it.distanceToSqr(player) }

        if (leader == null) {
            source.sendFailure(Component.literal("§cKhông tìm thấy herd leader nào trong bán kính 16 block!"))
            return 0
        }

        leader.discard()
        source.sendSuccess({ Component.literal("§cĐã loại bỏ herd leader! Hãy quan sát đàn tự động thăng chức con mới lên thay thế.") }, true)
        return 1
    }

    private fun executeTestAggro(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(player.position(), 48.0, 24.0, 48.0)

        val taurosList = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            CobblemonBridge.getSpeciesName(it) == "tauros"
        }

        if (taurosList.isEmpty()) {
            source.sendFailure(Component.literal("§cKhông tìm thấy đàn Tauros nào trong bán kính 24 block!"))
            return 0
        }

        val firstTauros = taurosList.first()
        val herdId = firstTauros.getHerdData().herdId
        if (herdId != null) {
            val lookDir = player.lookAngle
            com.toancao.pokemonai.behaviors.combat.HerdSharedAggroGoal.triggerHerdStampedeDirection(
                level, herdId, firstTauros.position(), lookDir, box
            )
            source.sendSuccess({ Component.literal("§c[STAMPEDE] Đã khóa hướng và phát động Đại Xung Phong toàn bầy Tauros chạy song song 40 block!") }, true)
        } else {
            firstTauros.target = player
            source.sendSuccess({ Component.literal("§aĐã kích hoạt mục tiêu tấn công cho Tauros đơn lẻ!") }, true)
        }
        return 1
    }

    private fun executePackLeave(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val target = getLookTargetPokemon(player) ?: run {
            source.sendFailure(Component.literal("§cKhông tìm thấy Pokémon nào trong tầm nhìn!"))
            return 0
        }

        val data = target.getHerdData()
        if (!data.isInHerd) {
            source.sendFailure(Component.literal("§eCon Pokémon này vốn đã là cá thể tự do độc lập (không thuộc đàn nào)."))
            return 0
        }

        data.herdId = null
        data.role = HerdRole.MEMBER
        data.leaderUUID = null
        data.isStampeding = false
        target.setHerdData(data)

        source.sendSuccess({ Component.literal("§a[Pack] Đã TÁCH con Pokémon này ra khỏi bầy đàn! Nó hiện là cá thể tự do độc lập.") }, true)
        return 1
    }

    private fun executePackJoin(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val target = getLookTargetPokemon(player) ?: run {
            source.sendFailure(Component.literal("§cKhông tìm thấy Pokémon nào trong tầm nhìn!"))
            return 0
        }

        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(target.position(), 48.0, 24.0, 48.0)
        val nearbyLeader = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            it != target && it.getHerdData().isLeader
        }.firstOrNull()

        if (nearbyLeader == null) {
            source.sendFailure(Component.literal("§cKhông tìm thấy bầy đàn nào có leader ở gần trong bán kính 24 block!"))
            return 0
        }

        val leaderData = nearbyLeader.getHerdData()
        val data = target.getHerdData()
        data.herdId = leaderData.herdId
        data.leaderUUID = nearbyLeader.uuid
        data.role = HerdRole.MEMBER
        data.formationIndex = level.random.nextInt(1, 12)
        target.setHerdData(data)

        source.sendSuccess({ Component.literal("§a[Pack] Đã GIA NHẬP con Pokémon này vào bầy đàn của thủ lĩnh gần nhất!") }, true)
        return 1
    }

    private fun executePackToggle(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val target = getLookTargetPokemon(player) ?: run {
            source.sendFailure(Component.literal("§cKhông tìm thấy Pokémon nào trong tầm nhìn!"))
            return 0
        }

        val data = target.getHerdData()
        if (data.isInHerd) {
            return executePackLeave(source)
        } else {
            return executePackJoin(source)
        }
    }

    private fun getLookTargetPokemon(player: net.minecraft.server.level.ServerPlayer): com.cobblemon.mod.common.entity.pokemon.PokemonEntity? {
        val level = player.serverLevel()
        val lookVec = player.lookAngle.scale(8.0)
        val searchBox = player.boundingBox.expandTowards(lookVec).inflate(2.0)
        return level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, searchBox) {
            it != player
        }.minByOrNull { it.distanceToSqr(player) }
    }

    private fun executePackNames(source: CommandSourceStack): Int {
        val player = source.player ?: return 0
        val level = player.serverLevel()
        val box = net.minecraft.world.phys.AABB.ofSize(player.position(), 128.0, 64.0, 128.0)

        val packEntities = level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java, box) {
            it.getHerdData().isInHerd
        }

        if (packEntities.isEmpty()) {
            source.sendFailure(Component.literal("§cKhông tìm thấy Pokémon bầy đàn nào trong bán kính 64 block!"))
            return 0
        }

        var updatedCount = 0
        for (poke in packEntities) {
            val data = poke.getHerdData()
            if (data.isLeader) {
                poke.customName = Component.literal("§6👑 [ĐẦU ĐÀN]")
                poke.isCustomNameVisible = true
            } else {
                poke.customName = Component.literal("§a🐂 [ĐÀN EM #${data.formationIndex}]")
                poke.isCustomNameVisible = true
            }
            updatedCount++
        }

        source.sendSuccess({ Component.literal("§a[Pack] Đã hiển thị NameTag chữ '§6👑 [ĐẦU ĐÀN]§a' và '§a🐂 [ĐÀN EM]§a' cho §e$updatedCount§a Pokémon!") }, true)
        return updatedCount
    }
}
