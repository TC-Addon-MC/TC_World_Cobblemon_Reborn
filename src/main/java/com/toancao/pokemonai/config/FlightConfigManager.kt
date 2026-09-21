package com.toancao.pokemonai.config

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.toancao.pokemonai.flight.FlightConfig
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class RootFlightConfigV2(
    var version: Int = 2,
    var pokemons: MutableMap<String, FlightConfig> = mutableMapOf()
)

data class RootFlightConfig(
    var version: Int = 3,
    var machineScanInterval: Int = 40,
    var machineUnloadDelay: Int = 200,
    var airSpawnChance: Double = 0.8,
    var airSpawnInterval: Int = 60,
    var airSpawnRadius: Double = 64.0,
    var pokemon_assignments: MutableMap<String, String>? = mutableMapOf(),
    var flight_presets: MutableMap<String, FlightConfig>? = mutableMapOf()
)

object FlightConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = FabricLoader.getInstance().configDir.resolve("tcworld_reborn").toFile()
    private val configFile = File(configDir, "config_pokeflight.json")

    var pokemonAssignments: MutableMap<String, String> = mutableMapOf()
        private set
    var flightPresets: MutableMap<String, FlightConfig> = mutableMapOf()
        private set

    var machineScanInterval: Int = 40
        private set
    var machineUnloadDelay: Int = 200
        private set
    var airSpawnChance: Double = 0.8
        private set
    var airSpawnInterval: Int = 60
        private set
    var airSpawnRadius: Double = 64.0
        private set

    val pokemonFlightConfigs: Map<String, FlightConfig>
        get() = pokemonAssignments.mapNotNull { (species, presetId) ->
            val preset = flightPresets[presetId]
            if (preset != null) species to preset else null
        }.toMap()

    fun loadConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                
                val rootType = object : TypeToken<RootFlightConfig>() {}.type
                var rootConfig: RootFlightConfig? = try {
                    gson.fromJson(json, rootType)
                } catch (e: Exception) { null }

                val loadedAssignments = rootConfig?.pokemon_assignments
                val loadedPresets = rootConfig?.flight_presets
                if (rootConfig != null && rootConfig.version == 3 && loadedAssignments != null && loadedPresets != null) {
                    pokemonAssignments = loadedAssignments
                    flightPresets = loadedPresets
                    machineScanInterval = rootConfig.machineScanInterval
                    machineUnloadDelay = rootConfig.machineUnloadDelay
                    airSpawnChance = rootConfig.airSpawnChance
                    airSpawnInterval = rootConfig.airSpawnInterval
                    airSpawnRadius = rootConfig.airSpawnRadius
                    
                    val defaultAssignments = mutableMapOf<String, String>()
                    val defaultPresets = mutableMapOf<String, FlightConfig>()
                    buildDefaultData(defaultAssignments, defaultPresets)
                    
                    defaultPresets.forEach { (k, v) -> flightPresets.putIfAbsent(k, v) }
                    defaultAssignments.forEach { (k, v) -> pokemonAssignments.putIfAbsent(k, v) }
                } else {
                    migrateToV3(json)
                }
                saveConfig()
            } catch (e: Exception) {
                buildDefaultConfigs()
                saveConfig()
                e.printStackTrace()
            }
        } else {
            buildDefaultConfigs()
            saveConfig()
        }
    }

    private fun migrateToV3(json: String) {
        val mapType = object : TypeToken<MutableMap<String, FlightConfig>>() {}.type
        val loadedMap: MutableMap<String, FlightConfig>? = try {
            val v2Type = object : TypeToken<RootFlightConfigV2>() {}.type
            val v2 = gson.fromJson<RootFlightConfigV2>(json, v2Type)
            if (v2 != null && v2.pokemons.isNotEmpty()) v2.pokemons else gson.fromJson(json, mapType)
        } catch (e: Exception) {
            try { gson.fromJson(json, mapType) } catch (e2: Exception) { null }
        }

        buildDefaultConfigs()

        if (loadedMap != null) {
            var customId = 100
            loadedMap.forEach { (species, userConfig) ->
                val defaultPresetId = pokemonAssignments[species]
                
                val safeConfig = if (userConfig.circularFlightRadius == 0.0) {
                    val fallback = defaultPresetId?.let { flightPresets[it] } ?: FlightConfig()
                    userConfig.copy(
                        circularFlightChance = fallback.circularFlightChance,
                        circularFlightRadius = fallback.circularFlightRadius,
                        circularFlightDuration = fallback.circularFlightDuration
                    )
                } else userConfig

                if (defaultPresetId != null) {
                    val defaultPreset = flightPresets[defaultPresetId]
                    if (defaultPreset != safeConfig) {
                        val newPresetId = "custom_$customId"
                        customId++
                        pokemonAssignments[species] = newPresetId
                        flightPresets[newPresetId] = safeConfig
                    }
                } else {
                    val newPresetId = "custom_$customId"
                    customId++
                    pokemonAssignments[species] = newPresetId
                    flightPresets[newPresetId] = safeConfig
                }
            }
        }
    }

    private fun buildDefaultData(assignments: MutableMap<String, String>, presets: MutableMap<String, FlightConfig>) {
        presets["1"] = FlightConfig(flightSpeed = 0.15, preferredHeight = 4.0, maxFlightTicks = 300, circularFlightChance = 0.5, circularFlightRadius = 8.0)
        presets["2"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 8.0, maxFlightTicks = 600, circularFlightChance = 0.5, circularFlightRadius = 10.0)
        presets["3"] = FlightConfig(flightSpeed = 0.275, preferredHeight = 12.0, maxFlightTicks = 1250, circularFlightChance = 0.5, circularFlightRadius = 12.0)
        presets["4"] = FlightConfig(flightSpeed = 0.3, preferredHeight = 17.5, maxFlightTicks = 1600, circularFlightChance = 0.5, circularFlightRadius = 15.0)
        presets["5"] = FlightConfig(flightSpeed = 0.35, preferredHeight = 22.5, maxFlightTicks = 2000, circularFlightChance = 0.5, circularFlightRadius = 15.0)
        presets["6"] = FlightConfig(flightSpeed = 0.375, preferredHeight = 30.0, maxFlightTicks = 3000, circularFlightChance = 0.5, circularFlightRadius = 20.0)

        presets["7"] = presets["1"]!!.copy(canGroundHover = true)
        presets["8"] = presets["2"]!!.copy(canGroundHover = true)
        presets["9"] = presets["6"]!!.copy(canGroundHover = true)
        presets["10"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 3.0, hoverOnly = true, canGroundHover = true, maxFlightTicks = 3000)
        presets["11"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 8.0, hoverOnly = true, canGroundHover = true, maxFlightTicks = 3000)
        presets["12"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 25.0, hoverOnly = true, canGroundHover = true, maxFlightTicks = 999999)
        presets["13"] = FlightConfig(flightSpeed = 0.25, preferredHeight = 25.0, canGroundHover = true, circularFlightChance = 0.0)
        presets["14"] = FlightConfig(flightSpeed = 0.4, preferredHeight = 8.0, canGroundHover = true, circularFlightChance = 0.0)

        presets["15"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 10.0, maxFlightTicks = 1200, circularFlightChance = 0.8, circularFlightRadius = 12.0, canGroundHover = true)
        presets["16"] = FlightConfig(flightSpeed = 0.25, preferredHeight = 15.0, maxFlightTicks = 5000, circularFlightChance = 0.8, circularFlightRadius = 20.0)
        presets["17"] = FlightConfig(flightSpeed = 0.12, preferredHeight = 8.0, maxFlightTicks = 1500, circularFlightChance = 0.8, circularFlightRadius = 10.0)
        presets["18"] = FlightConfig(flightSpeed = 0.4, preferredHeight = 15.0, maxFlightTicks = 2000, circularFlightChance = 0.8, circularFlightRadius = 18.0)
        presets["19"] = FlightConfig(flightSpeed = 0.18, preferredHeight = 35.0, maxFlightTicks = 5000, circularFlightChance = 0.7, circularFlightRadius = 30.0)

        presets["20"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 6.0, maxFlightTicks = 1500, waterHoverChance = 0.8, circularFlightChance = 0.3)
        presets["21"] = FlightConfig(flightSpeed = 0.3, preferredHeight = 5.0, waterHoverChance = 1.0, canGroundHover = true)
        presets["22"] = FlightConfig(flightSpeed = 0.35, preferredHeight = 6.0, waterHoverChance = 0.8)
        presets["23"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 3.0, hoverOnly = true, waterHoverChance = 1.0, canGroundHover = true)

        presets["24"] = FlightConfig(flightSpeed = 0.25, preferredHeight = 20.0, maxFlightTicks = 999999, baseLandingChance = 0.0, circularFlightChance = 0.5)
        presets["25"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 35.0, baseLandingChance = 0.0, maxFlightTicks = 999999, circularFlightChance = 0.7, circularFlightRadius = 8.0)

        presets["26"] = FlightConfig(flightSpeed = 0.1, preferredHeight = 30.0, maxFlightTicks = 3000, circularFlightChance = 0.2)
        presets["27"] = FlightConfig(flightSpeed = 0.45, preferredHeight = 5.0, maxFlightTicks = 1000, circularFlightChance = 0.1)
        presets["28"] = FlightConfig(flightSpeed = 0.3, preferredHeight = 12.0, circularFlightChance = 0.2)
        presets["29"] = FlightConfig(flightSpeed = 0.4, preferredHeight = 3.0, circularFlightChance = 0.9, circularFlightRadius = 4.0, canGroundHover = true)
        presets["30"] = FlightConfig(flightSpeed = 0.45, preferredHeight = 25.0, maxFlightTicks = 4000, circularFlightChance = 0.1, baseLandingChance = 0.05)
        presets["31"] = FlightConfig(flightSpeed = 0.45, preferredHeight = 4.0, circularFlightChance = 0.8, circularFlightRadius = 8.0)
        presets["32"] = FlightConfig(flightSpeed = 0.48, preferredHeight = 12.0, circularFlightChance = 0.0)

        try {
            val inputStream = FlightConfigManager::class.java.getResourceAsStream("/pokemon_flight_tiers.json")
            if (inputStream != null) {
                val jsonStr = inputStream.bufferedReader().use { it.readText() }
                val mapType = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                val tiersByGen: Map<String, Map<String, String>> = gson.fromJson(jsonStr, mapType)
                tiersByGen.values.forEach { genMap ->
                    genMap.forEach { (species, tier) ->
                        assignments["cobblemon:${species}"] = tier
                    }
                }
            } else {
                println("[PokemonAI] Could not find /pokemon_flight_tiers.json in resources!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildDefaultConfigs() {
        pokemonAssignments.clear()
        flightPresets.clear()
        buildDefaultData(pokemonAssignments, flightPresets)
    }

    fun saveConfig() {
        try {
            val rootConfig = RootFlightConfig(
                version = 3,
                machineScanInterval = machineScanInterval,
                machineUnloadDelay = machineUnloadDelay,
                airSpawnChance = airSpawnChance,
                airSpawnInterval = airSpawnInterval,
                airSpawnRadius = airSpawnRadius,
                pokemon_assignments = pokemonAssignments,
                flight_presets = flightPresets
            )
            val json = gson.toJson(rootConfig)
            configFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
