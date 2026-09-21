package com.toancao.pokemonai.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class HerdPokemonConfig(
    var minPackSize: Int = 6,
    var maxPackSize: Int = 12,
    var naturalSpawnHerdChance: Float = 0.75f,
    var leaderScale: Float = 1.25f,
    var memberScaleMin: Float = 0.95f,
    var memberScaleMax: Float = 1.05f,
    var minLeaderLevel: Int = 38,
    var maxLeaderLevel: Int = 48,
    var stampedeMarchSpeed: Double = 0.55,
    var maxBrokenBlocksBeforeStop: Int = 5,
    var hornClashMinCooldownTicks: Int = 1200,
    var hornClashMaxCooldownTicks: Int = 2400,
    var hornClashRandomChance: Int = 60
)

data class HerdSystemConfig(
    var tauros: HerdPokemonConfig = HerdPokemonConfig(
        minPackSize = 6,
        maxPackSize = 14,
        naturalSpawnHerdChance = 0.75f,
        leaderScale = 1.25f,
        minLeaderLevel = 38,
        maxLeaderLevel = 48
    ),
    var bouffalant: HerdPokemonConfig = HerdPokemonConfig(
        minPackSize = 6,
        maxPackSize = 12,
        naturalSpawnHerdChance = 0.75f,
        leaderScale = 1.25f,
        minLeaderLevel = 38,
        maxLeaderLevel = 50
    )
)

object HerdConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = FabricLoader.getInstance().configDir.resolve("tcworld_reborn").toFile()
    private val configFile = File(configDir, "config_herd.json")

    var config = HerdSystemConfig()
        private set

    fun loadConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                config = gson.fromJson(json, HerdSystemConfig::class.java) ?: HerdSystemConfig()
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

    fun getConfigForSpecies(species: String): HerdPokemonConfig {
        return when (species.lowercase()) {
            "bouffalant" -> config.bouffalant
            else -> config.tauros
        }
    }
}
