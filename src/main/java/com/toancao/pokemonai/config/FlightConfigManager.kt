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

    var machineScanInterval: Int = 40
        private set
    var machineUnloadDelay: Int = 200
        private set
    var airSpawnChance: Double = 0.8
        private set
    var airSpawnInterval: Int = 60
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
                    machineScanInterval = rootConfig.machineScanInterval
                    machineUnloadDelay = rootConfig.machineUnloadDelay
                    airSpawnChance = rootConfig.airSpawnChance
                    airSpawnInterval = rootConfig.airSpawnInterval
                    
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
        // --- 1-6 Basic (kích thước) ---
        presets["1"] = FlightConfig(flightSpeed = 0.15, preferredHeight = 4.0, maxFlightTicks = 300, circularFlightChance = 0.5, circularFlightRadius = 8.0) // Cỡ siêu nhỏ, bay chậm và thấp, thể lực yếu nên nhanh chóng phải hạ cánh nghỉ ngơi, thích hợp cho các loài chim hoặc bọ non mới nở.
        presets["2"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 8.0, maxFlightTicks = 600, circularFlightChance = 0.5, circularFlightRadius = 10.0) // Cỡ nhỏ, bay ở tốc độ và độ cao trung bình, thể lực vừa phải, thích hợp cho các loài chim hoặc côn trùng đã tiến hóa cấp một.
        presets["3"] = FlightConfig(flightSpeed = 0.275, preferredHeight = 12.0, maxFlightTicks = 1250, circularFlightChance = 0.5, circularFlightRadius = 12.0) // Cỡ vừa, bay khá nhanh ở độ cao tương đối lớn, thể lực dồi dào hơn cho phép duy trì chuyến bay lâu dài, dành cho chim trưởng thành.
        presets["4"] = FlightConfig(flightSpeed = 0.3, preferredHeight = 17.5, maxFlightTicks = 1600, circularFlightChance = 0.5, circularFlightRadius = 15.0) // Cỡ lớn, bay nhanh và sải cánh rộng ở độ cao cao, thể lực rất trâu bò, thường là các loài rồng cỡ trung hoặc thú cưỡi trên không.
        presets["5"] = FlightConfig(flightSpeed = 0.35, preferredHeight = 22.5, maxFlightTicks = 2000, circularFlightChance = 0.5, circularFlightRadius = 15.0) // Cỡ khổng lồ, tốc độ bay cực kỳ nhanh và hoạt động ở tầng không gian rất cao, sức chịu đựng khổng lồ, phù hợp với chim chúa huyền thoại.
        presets["6"] = FlightConfig(flightSpeed = 0.375, preferredHeight = 30.0, maxFlightTicks = 3000, circularFlightChance = 0.5, circularFlightRadius = 20.0) // Thần thoại và Boss, bay với tốc độ xé gió ở độ cao không tưởng, thể lực gần như vô tận, dành riêng cho siêu rồng hoặc thực thể tối thượng.

        // --- 7-14 Hover ---
        presets["7"] = presets["1"]!!.copy(canGroundHover = true) // Lơ lửng cỡ nhỏ, bay tà tà sát mặt đất nhưng mang đặc tính có thể đáp xuống đất nghỉ ngơi, dành cho các loài bay yếu hoặc lơ lửng chậm chạp.
        presets["8"] = presets["2"]!!.copy(canGroundHover = true) // Lơ lửng cỡ trung, di chuyển ở tốc độ và độ cao vừa phải, có khả năng đậu xuống đất khi hết thể lực, phù hợp với các loài trung bình.
        presets["9"] = presets["6"]!!.copy(canGroundHover = true) // Lơ lửng thần thoại, di chuyển nhanh ở độ cao lớn nhưng thỉnh thoảng vẫn có thể đáp xuống mặt đất để tương tác, thiết kế cho các thực thể mạnh mẽ.
        presets["10"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 3.0, hoverOnly = true, canGroundHover = true, maxFlightTicks = 3000) // Chỉ lơ lửng ở độ cao thấp tầm 3 blocks, hoàn toàn không bao giờ bay đi chỗ khác, tạo cảm giác tĩnh lặng bí ẩn cho các loài ma quỷ nhỏ.
        presets["11"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 8.0, hoverOnly = true, canGroundHover = true, maxFlightTicks = 3000) // Chỉ lơ lửng ở độ cao trung bình tầm 8 blocks, duy trì vị trí trên không trung liên tục mà không di chuyển xa, phù hợp cho các loài lơ lửng.
        presets["12"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 25.0, hoverOnly = true, canGroundHover = true, maxFlightTicks = 999999) // Chỉ lơ lửng ở độ cao rất lớn tầm 25 blocks, hoạt động như một trạm gác hoặc thực thể quan sát từ trên cao, thể lực vô hạn không bao giờ đáp.
        presets["13"] = FlightConfig(flightSpeed = 0.25, preferredHeight = 25.0, canGroundHover = true, circularFlightChance = 0.0) // Bay cao nhưng thỉnh thoảng sẽ khựng lại lơ lửng giữa không trung một lúc rồi mới tiếp tục bay, tạo cảm giác di chuyển ngắt quãng đầy tính chiến thuật.
        presets["14"] = FlightConfig(flightSpeed = 0.4, preferredHeight = 8.0, canGroundHover = true, circularFlightChance = 0.0) // Hover nhanh nhưng không bao giờ bay lượn vòng tròn, di chuyển theo đường thẳng dứt khoát như một chiếc Drone cơ khí hoặc các Boss yêu cầu tính cơ động cao.

        // --- 15-19 Circular ---
        presets["15"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 10.0, maxFlightTicks = 1200, circularFlightChance = 0.8, circularFlightRadius = 12.0, canGroundHover = true) // Bay lượn vòng tròn và có thể lơ lửng, tốc độ trung bình, thường bay theo các quỹ đạo khép kín quanh một khu vực cố định để kiểm soát lãnh thổ.
        presets["16"] = FlightConfig(flightSpeed = 0.25, preferredHeight = 15.0, maxFlightTicks = 5000, circularFlightChance = 0.8, circularFlightRadius = 20.0) // Bay lượn vòng tròn với thể lực rất lớn, di chuyển trên không gian rộng lớn, thích hợp cho các loài bay lượn tuần tra bảo vệ khu vực trong thời gian dài.
        presets["17"] = FlightConfig(flightSpeed = 0.12, preferredHeight = 8.0, maxFlightTicks = 1500, circularFlightChance = 0.8, circularFlightRadius = 10.0) // Bay lượn vòng tròn kết hợp di chuyển rất chậm chạp, giữ độ cao thấp, phù hợp với những sinh vật bay lững thững thư giãn quanh khu vực sinh sống của chúng.
        presets["18"] = FlightConfig(flightSpeed = 0.4, preferredHeight = 15.0, maxFlightTicks = 2000, circularFlightChance = 0.8, circularFlightRadius = 18.0) // Bay lượn vòng tròn với tốc độ rất nhanh, lượn theo quỹ đạo tương đối lớn để tìm kiếm con mồi, mô phỏng hoàn hảo tập tính của các loài chim đại bàng.
        presets["19"] = FlightConfig(flightSpeed = 0.18, preferredHeight = 35.0, maxFlightTicks = 5000, circularFlightChance = 0.7, circularFlightRadius = 30.0) // Bay lượn nhàn nhã trên bầu trời rất cao, lượn những vòng tròn khổng lồ và hiếm khi hạ cánh, rất phù hợp cho các loài chim ưng hoặc rồng bay lượn.

        // --- 20-23 Water ---
        presets["20"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 6.0, maxFlightTicks = 1500, waterHoverChance = 0.8, circularFlightChance = 0.3) // Ưu tiên lơ lửng sát mặt nước thay vì mặt đất, tốc độ bay trung bình, thiết kế dành riêng cho các loài Pokemon hệ Nước hoặc hệ Bay sống quanh vùng hồ.
        presets["21"] = FlightConfig(flightSpeed = 0.3, preferredHeight = 5.0, waterHoverChance = 1.0, canGroundHover = true) // Chuyên tuần tra và lơ lửng trên mặt nước, di chuyển nhanh nhẹn ở độ cao thấp, hoàn hảo cho các loài chim nước như Pelipper hay Swanna muốn bám sát mặt hồ.
        presets["22"] = FlightConfig(flightSpeed = 0.35, preferredHeight = 6.0, waterHoverChance = 0.8) // Bay nhanh và luôn ưu tiên giữ vị trí trên mặt nước, thích hợp cho các loài săn cá hoặc di chuyển tốc độ cao dọc theo các dòng sông và bãi biển.
        presets["23"] = FlightConfig(flightSpeed = 0.0, preferredHeight = 3.0, hoverOnly = true, waterHoverChance = 1.0, canGroundHover = true) // Chỉ lơ lửng cố định trên mặt nước một cách tĩnh lặng, không bao giờ đáp xuống đất liền, cực kỳ thích hợp cho các loài như hoa súng hay thủy thần.

        // --- 24-25 Endless ---
        presets["24"] = FlightConfig(flightSpeed = 0.25, preferredHeight = 20.0, maxFlightTicks = 999999, baseLandingChance = 0.0, circularFlightChance = 0.5) // Bay liên tục không bao giờ nghỉ ngơi, thể lực vô hạn và không có tỷ lệ hạ cánh, lượn vòng nhẹ nhàng, dành cho các loài thú cưỡi vi vu trên không.
        presets["25"] = FlightConfig(flightSpeed = 0.2, preferredHeight = 35.0, baseLandingChance = 0.0, maxFlightTicks = 999999, circularFlightChance = 0.7, circularFlightRadius = 8.0) // Bay lượn vô hạn trên đỉnh trời, bay theo quỹ đạo khổng lồ mà không bao giờ cạn kiệt thể lực hay đáp xuống, chuẩn mực cho rồng thần và đại bàng chúa.

        // --- 26-32 Special ---
        presets["26"] = FlightConfig(flightSpeed = 0.1, preferredHeight = 30.0, maxFlightTicks = 3000, circularFlightChance = 0.2) // Bay di chuyển rất chậm nhưng ở độ cao cực kỳ lớn, không thích lượn vòng, lững thững trôi dạt trên bầu trời y hệt như những đám mây hoặc khinh khí cầu.
        presets["27"] = FlightConfig(flightSpeed = 0.45, preferredHeight = 5.0, maxFlightTicks = 1000, circularFlightChance = 0.1) // Bay cực kỳ nhanh nhưng bám rất sát mặt đất, rất ít khi lượn vòng, thể lực ngắn hạn, mô phỏng các pha lao dốc chớp nhoáng của chim cắt săn mồi.
        presets["28"] = FlightConfig(flightSpeed = 0.3, preferredHeight = 12.0, circularFlightChance = 0.2) // Bay hoàn toàn ngẫu nhiên và khó đoán, đổi hướng liên tục và rất ít khi bay theo quỹ đạo vòng tròn, tốc độ khá, phù hợp với các loài côn trùng.
        presets["29"] = FlightConfig(flightSpeed = 0.4, preferredHeight = 3.0, circularFlightChance = 0.9, circularFlightRadius = 4.0, canGroundHover = true) // Tốc độ bay cao, lượn những vòng cực kỳ hẹp sát mặt đất, tạo ra cảm giác lanh lẹ thoắt ẩn thoắt hiện, hoàn hảo cho hệ Tiên hoặc họ chim ruồi.
        presets["30"] = FlightConfig(flightSpeed = 0.45, preferredHeight = 25.0, maxFlightTicks = 4000, circularFlightChance = 0.1, baseLandingChance = 0.05) // Mang phong cách săn mồi đáng sợ, ít lượn vòng, duy trì tốc độ cực cao trên không trung và rất hiếm khi đáp xuống, luôn sẵn sàng cho pha bổ nhào.
        presets["31"] = FlightConfig(flightSpeed = 0.45, preferredHeight = 4.0, circularFlightChance = 0.8, circularFlightRadius = 8.0) // Bay bám sát mặt đất với tốc độ cực kỳ kinh hoàng, kết hợp lượn vòng liên tục, thiết kế chuẩn xác nhất cho tập tính bay ngoắt ngoéo của họ nhà dơi.
        presets["32"] = FlightConfig(flightSpeed = 0.48, preferredHeight = 12.0, circularFlightChance = 0.0) // Tốc độ di chuyển siêu thanh, hoàn toàn không bay vòng mà chỉ đổi hướng đột ngột liên tục, cực kỳ phù hợp cho ruồi, ong vò vẽ hoặc sinh vật nhỏ linh hoạt.

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