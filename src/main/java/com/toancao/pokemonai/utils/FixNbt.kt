package com.toancao.pokemonai.utils

import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.CompoundTag
import java.io.File
import java.nio.file.Path

object FixNbt {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val file = File("d:/Minecraft/allmod/TC_world_reborn_1.21.1_1.7.3/src/main/resources/data/tc_reborn/structure/magikarp_dragon_evolution_lake.nbt")
            if (!file.exists()) {
                println("File not found: ${file.absolutePath}")
                return
            }
            val path = file.toPath()
            val nbt = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
            
            var modified = false

            // Fix palette
            if (nbt.contains("palette", 9)) {
                val palette = nbt.getList("palette", 10)
                for (i in 0 until palette.size) {
                    val state = palette.getCompound(i)
                    val name = state.getString("Name")
                    if (name.startsWith("tcpoke_reborn:")) {
                        state.putString("Name", name.replace("tcpoke_reborn:", "tc_reborn:"))
                        modified = true
                    }
                }
            }
            
            // If there are multiple palettes (e.g. "palettes")
            if (nbt.contains("palettes", 9)) {
                val palettes = nbt.getList("palettes", 9)
                for (i in 0 until palettes.size) {
                    val palette = palettes.getList(i)
                    for (j in 0 until palette.size) {
                        val state = palette.getCompound(j)
                        val name = state.getString("Name")
                        if (name.startsWith("tcpoke_reborn:")) {
                            state.putString("Name", name.replace("tcpoke_reborn:", "tc_reborn:"))
                            modified = true
                        }
                    }
                }
            }
            
            // Fix block entities
            if (nbt.contains("blocks", 9)) {
                val blocks = nbt.getList("blocks", 10)
                for (i in 0 until blocks.size) {
                    val block = blocks.getCompound(i)
                    if (block.contains("nbt", 10)) {
                        val entityNbt = block.getCompound("nbt")
                        val id = entityNbt.getString("id")
                        if (id.startsWith("tcpoke_reborn:")) {
                            entityNbt.putString("id", id.replace("tcpoke_reborn:", "tc_reborn:"))
                            modified = true
                        }
                    }
                }
            }

            if (modified) {
                NbtIo.writeCompressed(nbt, path)
                println("Successfully fixed NBT file!")
            } else {
                println("No changes needed for NBT file.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
