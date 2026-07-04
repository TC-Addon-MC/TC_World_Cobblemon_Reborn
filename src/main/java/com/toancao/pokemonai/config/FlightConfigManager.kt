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
    var pokemon_assignments: MutableMap<String, String> = mutableMapOf(),
    var flight_presets: MutableMap<String, FlightConfig> = mutableMapOf()
)

object FlightConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir = FabricLoader.getInstance().configDir.resolve("tcworld_reborn").toFile()
    private val configFile = File(configDir, "config_pokeflight.json")

    var pokemonAssignments: MutableMap<String, String> = mutableMapOf()
        private set
    var flightPresets: MutableMap<String, FlightConfig> = mutableMapOf()
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
                
                // Cố gắng đọc theo định dạng V3
                val rootType = object : TypeToken<RootFlightConfig>() {}.type
                var rootConfig: RootFlightConfig? = try {
                    gson.fromJson(json, rootType)
                } catch (e: Exception) { null }

                if (rootConfig != null && rootConfig.version == 3 && rootConfig.pokemon_assignments != null && rootConfig.flight_presets != null) {
                    pokemonAssignments = rootConfig.pokemon_assignments
                    flightPresets = rootConfig.flight_presets
                    
                    // Đắp thêm các pokemon mặc định nếu thiếu
                    val defaultAssignments = mutableMapOf<String, String>()
                    val defaultPresets = mutableMapOf<String, FlightConfig>()
                    buildDefaultData(defaultAssignments, defaultPresets)
                    
                    defaultPresets.forEach { (k, v) -> flightPresets.putIfAbsent(k, v) }
                    defaultAssignments.forEach { (k, v) -> pokemonAssignments.putIfAbsent(k, v) }
                } else {
                    // Cấu trúc cũ (V1 hoặc V2) -> Migration lên V3
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
                
                // Cập nhật các trường lượn vòng nếu thiếu (vì V1 không có)
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
        presets["7"] = presets["1"]!!.copy(canGroundHover = true) // Lơ lửng cỡ nhỏ
        presets["8"] = presets["2"]!!.copy(canGroundHover = true) // Lơ lửng cỡ trung
        presets["9"] = presets["6"]!!.copy(canGroundHover = true) // Lơ lửng thần thoại

        // Preset 1: Chim nhỏ / Bọ / Dơi nhỏ
        val tier1 = listOf("pidgey", "spearow", "zubat", "hoothoot", "taillow", "wingull", "starly", "pidove", "fletchling", "pikipek", "rookidee", "natu", "murkrow", "yanma", "woobat", "rufflet", "vullaby", "noibat", "emolga", "wattrel", "squawkabilly", "ducklett")
        // Preset 2: Chim trung bình / Bọ tiến hóa / Dơi
        val tier2 = listOf("pidgeotto", "golbat", "noctowl", "swellow", "pelipper", "staravia", "tranquill", "fletchinder", "trumbeak", "corvisquire", "xatu", "honchkrow", "yanmega", "swoobat", "butterfree", "beedrill", "venomoth", "mothim", "ninjask", "masquerain", "vivillon", "scyther", "delibird", "dustox", "vibrava", "togetic", "gligar", "swanna")
        // Preset 3: Chim lớn / Thằn lằn bay
        val tier3 = listOf("fearow", "aerodactyl", "skarmory", "staraptor", "unfezant", "talonflame", "toucannon", "corviknight", "braviary", "mandibuzz", "swablu", "altaria", "tropius", "archeops", "sigilyph", "hawlucha", "oricorio", "cramorant", "bombirdier", "flamigo", "mantine", "kilowattrel")
        // Preset 4: Rồng cỡ trung / Thú cưỡi bay
        val tier4 = listOf("charizard", "flygon", "salamence", "togekiss", "crobat", "gliscor", "volcarona", "noivern", "latios", "latias", "iron_moth")
        // Preset 5: Chim chúa / Chim huyền thoại tốc độ cao
        val tier5 = listOf("pidgeot", "articuno", "zapdos", "moltres", "tornadus", "thundurus", "landorus", "roaring_moon", "iron_jugulis")
        // Preset 6: Siêu rồng / Huyền thoại tối thượng
        val tier6 = listOf("dragonite", "rayquaza", "lugia", "ho_oh", "hydreigon", "reshiram", "zekrom", "kyurem", "yveltal", "dragapult")
        // Preset 7: Lơ lửng nhỏ
        val tier7 = listOf("magnemite", "gastly", "rotom", "drifloon", "koffing", "geodude", "baltoy", "bronzor")
        // Preset 8: Lơ lửng trung bình
        val tier8 = listOf("magneton", "haunter", "drifblim", "weezing", "claydol", "bronzong")
        // Preset 9: Lơ lửng thần thoại
        val tier9 = listOf("mew", "mewtwo", "lunala")


        tier1.forEach { assignments["cobblemon:$it"] = "1" }
        tier2.forEach { assignments["cobblemon:$it"] = "2" }
        tier3.forEach { assignments["cobblemon:$it"] = "3" }
        tier4.forEach { assignments["cobblemon:$it"] = "4" }
        tier5.forEach { assignments["cobblemon:$it"] = "5" }
        tier6.forEach { assignments["cobblemon:$it"] = "6" }
        tier7.forEach { assignments["cobblemon:$it"] = "7" }
        tier8.forEach { assignments["cobblemon:$it"] = "8" }
        tier9.forEach { assignments["cobblemon:$it"] = "9" }
    }

    private fun buildDefaultConfigs() {
        pokemonAssignments.clear()
        flightPresets.clear()
        buildDefaultData(pokemonAssignments, flightPresets)
    }

    fun saveConfig() {
        try {
            val rootConfig = RootFlightConfig(version = 3, pokemon_assignments = pokemonAssignments, flight_presets = flightPresets)
            val json = gson.toJson(rootConfig)
            configFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
