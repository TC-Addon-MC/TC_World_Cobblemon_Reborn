package com.toancao.pokemonai.events

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class DragonGateData(
    val dimension: String,
    val startX: Int, val startY: Int, val startZ: Int,
    val endX: Int, val endY: Int, val endZ: Int
)

object DragonGateManager {
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: File
    
    val gates = mutableListOf<DragonGateData>()
    
    init {
        val configDir = FabricLoader.getInstance().configDir.resolve("pokemonai").toFile()
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        configFile = File(configDir, "dragon_gates.json")
        load()
    }

    fun load() {
        if (configFile.exists()) {
            try {
                val type = object : TypeToken<List<DragonGateData>>() {}.type
                FileReader(configFile).use { reader ->
                    val data: List<DragonGateData>? = GSON.fromJson(reader, type)
                    if (data != null) {
                        gates.clear()
                        gates.addAll(data)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun save() {
        try {
            FileWriter(configFile).use { writer ->
                GSON.toJson(gates, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addGate(dimension: String, startPos: BlockPos, endPos: BlockPos) {
        val gate = DragonGateData(
            dimension,
            startPos.x, startPos.y, startPos.z,
            endPos.x, endPos.y, endPos.z
        )
        gates.add(gate)
        save()
    }

    fun getGatesInDimension(dimension: String): List<DragonGateData> {
        return gates.filter { it.dimension == dimension }
    }
}
