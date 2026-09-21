package com.toancao.pokemonai.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class AronForageConfig(
    var scanInterval: Int = 40,
    var horizontalRayRange: Int = 14,
    var downwardRayRange: Int = 10,
    var diagonalRayRange: Int = 14,
    var pathSearchPadding: Int = 4,
    var pathSearchMaxNodes: Int = 4096,
    var pathBreakCost: Double = 6.0,
    var waypointDistance: Double = 0.7,
    var moveSpeed: Double = 0.55,
    var interactionDistance: Double = 1.15,
    var itemPickupDistance: Double = 0.75,
    var dropSearchTicks: Int = 40,
    var fetchScanXZ: Double = 6.0,
    var fetchScanY: Double = 4.0,
    var gnawTicks: Int = 100,
    var gnawSoundInterval: Int = 20,
    var gnawParticleInterval: Int = 4,
    var gnawParticleCount: Int = 6,
    var digTicks: Int = 60,
    var digSoundInterval: Int = 20,
    var digParticleInterval: Int = 4,
    var digParticleCount: Int = 6,
    var eatNibbleTicks: Int = 30,
    var eatSoundInterval: Int = 15,
    var crackViewDistance: Double = 64.0,
    var repathInterval: Int = 20,
    var maxDigBlocks: Int = 8,
    var maxActiveTicks: Int = 1200,
    var shortRest: Int = 200,
    var longRest: Int = 2400,
    var mealRest: Int = 1200,
    var xpFraction: Int = 20,
    var ironFoodBlocks: List<String> = listOf(
        "minecraft:iron_ore",
        "minecraft:deepslate_iron_ore",
        "minecraft:raw_iron_block",
        "minecraft:iron_block",
        "minecraft:iron_bars",
        "minecraft:chain",
        "minecraft:rail",
        "minecraft:powered_rail",
        "minecraft:detector_rail",
        "minecraft:activator_rail",
        "minecraft:anvil",
        "minecraft:chipped_anvil",
        "minecraft:damaged_anvil"
    ),
    var diggableBlocks: List<String> = listOf(
    // Đất / cát
    "minecraft:dirt",
    "minecraft:grass_block",
    "minecraft:coarse_dirt",
    "minecraft:gravel",
    "minecraft:sand",
    "minecraft:red_sand",
    "minecraft:clay",
    "minecraft:mud",

    // Đá phổ biến
    "minecraft:stone",
    "minecraft:cobblestone",
    "minecraft:deepslate",
    "minecraft:cobbled_deepslate",
    "minecraft:tuff",
    "minecraft:granite",
    "minecraft:diorite",
    "minecraft:andesite",

    // Gỗ phổ biến
    "minecraft:oak_log",
    "minecraft:oak_planks",
    "minecraft:oak_slab",

    "minecraft:spruce_log",
    "minecraft:spruce_planks",
    "minecraft:spruce_slab",

    "minecraft:birch_log",
    "minecraft:birch_planks",
    "minecraft:birch_slab",

    "minecraft:jungle_log",
    "minecraft:jungle_planks",
    "minecraft:jungle_slab",

    "minecraft:acacia_log",
    "minecraft:acacia_planks",
    "minecraft:acacia_slab",

    "minecraft:dark_oak_log",
    "minecraft:dark_oak_planks",
    "minecraft:dark_oak_slab",

    "minecraft:mangrove_log",
    "minecraft:mangrove_planks",
    "minecraft:mangrove_slab",

    "minecraft:cherry_log",
    "minecraft:cherry_planks",
    "minecraft:cherry_slab",

    // Nether phổ biến
    "minecraft:netherrack",
    "minecraft:soul_sand",
    "minecraft:soul_soil",
    "minecraft:blackstone",
    "minecraft:basalt"
)
)

object AronConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = FabricLoader.getInstance().configDir.resolve("tcworld_reborn").toFile()
    private val configFile = File(configDir, "config_aron.json")

    var config = AronForageConfig()
        private set

    fun loadConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                config = gson.fromJson(json, AronForageConfig::class.java) ?: AronForageConfig()
                saveConfig()
            } catch (e: Exception) {
                e.printStackTrace()
                saveConfig()
            }
        } else {
            saveConfig()
        }
    }

    fun saveConfig() {
        try {
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            val json = gson.toJson(config)
            configFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
