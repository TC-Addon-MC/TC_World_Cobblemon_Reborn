package com.toancao.pokemonai.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class MagikarpEventConfig(
    var eventIntervalMultiplier: Int = 3,
    var swimmingPhaseDurationTicks: Int = 17000,
    var evolvingPhaseDurationTicks: Int = 600,
    var maxMagikarpEvolutionRadius: Double = 40.0,
    var jumpVelocityMin: Double = 1.2,
    var jumpVelocityMaxOffset: Double = 0.4,
    var requiredLevelForEvolution: Int = 20,
    var baseRage: Int = 0,
    var baseDetermination: Int = 20,
    var baseFear: Int = 30
)

object MagikarpConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = FabricLoader.getInstance().configDir.resolve("tcworld_reborn").toFile()
    private val configFile = File(configDir, "config_magicap.json")

    var config = MagikarpEventConfig()
        private set

    fun loadConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                config = gson.fromJson(json, MagikarpEventConfig::class.java) ?: MagikarpEventConfig()
                // Save again to ensure new fields are written
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
