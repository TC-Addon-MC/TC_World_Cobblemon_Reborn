@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.evolution

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.utils.EntityUtils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity

object EvolutionManager {
    class ScheduledTask(var delayTicks: Int, val action: () -> Unit)
    private val scheduledTasks = mutableListOf<ScheduledTask>()

    // Theo dõi các Pokemon đang trong quá trình tiến hóa để tránh trigger lại trong lúc chờ
    private val evolving = mutableSetOf<java.util.UUID>()

    // Lên lịch thực thi một hành động sau một khoảng thời gian delay tính bằng tick.
    fun scheduleTask(delayTicks: Int, action: () -> Unit) {
        scheduledTasks.add(ScheduledTask(delayTicks, action))
    }

    // Đăng ký sự kiện tick của server để xử lý các task và kiểm tra tiến hóa định kỳ.
    fun register() {
        var tickCounter = 0
        ServerTickEvents.END_WORLD_TICK.register { world: ServerLevel ->
            val toExecute = mutableListOf<ScheduledTask>()
            val iterator = scheduledTasks.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                task.delayTicks--
                if (task.delayTicks <= 0) {
                    iterator.remove()
                    toExecute.add(task)
                }
            }
            toExecute.forEach { it.action() }

            tickCounter++
            if (tickCounter >= 20) {
                tickCounter = 0
                tick(world)
            }
        }
    }

    // Lặp qua tất cả entity trong thế giới, tìm Pokemon hoang dã để kiểm tra điều kiện tiến hóa.
    private fun tick(world: ServerLevel) {
        val entities = world.getAllEntities().toList()
        entities.forEach { entity ->
            if (CobblemonBridge.checkIsPokemonEntity(entity)) {
                val pokemon = CobblemonBridge.castToPokemonEntity(entity)
                if (CobblemonBridge.isWild(pokemon)) {
                    checkEvolution(pokemon, entity)
                }
            }
        }
    }

    // Lấy luật tiến hóa của Pokemon theo loài và áp dụng nếu đạt đủ điều kiện cảm xúc, trạng thái.
    private fun checkEvolution(pokemon: PokemonEntity, entity: Entity) {
        val species = CobblemonBridge.getSpeciesName(pokemon)
        val rules = EvolutionRegistry.getRules(species)
        if (rules.isEmpty()) return

        val emotion = EntityUtils.getEmotion(entity)
        val state = EntityUtils.getEvolutionState(entity)

        for (rule in rules) {
            val result = rule.check(pokemon, emotion, state)
            if (result != null) {
                evolve(pokemon, result)
                break
            }
        }
    }

    // Kích hoạt quá trình tiến hóa bắt buộc và áp dụng các thay đổi chỉ số sau khi tiến hóa.
    private fun evolve(pokemon: PokemonEntity, result: EvolutionResult) {
        forceEvolve(pokemon, result.targetSpecies)
        result.modifiers(pokemon)
    }

    // Ép Pokemon hoang dã tiến hóa thành loài mới với hiệu ứng xoáy và ánh sáng.
    fun forceEvolve(pokemon: PokemonEntity, targetSpecies: String) {
        if (!CobblemonBridge.isWild(pokemon)) return

        // Tránh trigger lại trong khi đang đợi animation (50 tick)
        val pokemonId = CobblemonBridge.getEntityUUID(pokemon)
        if (pokemonId in evolving) return
        evolving.add(pokemonId)

        val pokemonData = CobblemonBridge.getPokemonData(pokemon)
        val newSpecies = PokemonSpecies.getByName(targetSpecies) ?: run {
            evolving.remove(pokemonId)
            return
        }
        val level = CobblemonBridge.getLevel(pokemon) as? ServerLevel ?: run {
            evolving.remove(pokemonId)
            return
        }

        // === Phase 1: Beam bắt đầu + xoáy particles (tick 5 → 50) ===
        CobblemonBridge.setBeamMode(pokemon, 1)

        // 10 lần vòng xoáy PORTAL particles, mỗi vòng cách 5 tick
        for (i in 1..10) {
            val tickDelay = i * 5
            val radius = 0.6 + i * 0.12
            val height = i * 0.18
            val baseAngle = i * 1.25 // xoay góc dần theo mỗi vòng
            scheduleTask(tickDelay) {
                val ex = CobblemonBridge.getX(pokemon)
                val ey = CobblemonBridge.getY(pokemon)
                val ez = CobblemonBridge.getZ(pokemon)
                for (j in 0..7) {
                    val angle = baseAngle + j * (Math.PI * 2.0 / 8.0)
                    val px = ex + radius * Math.cos(angle)
                    val pz = ez + radius * Math.sin(angle)
                    level.sendParticles(ParticleTypes.PORTAL, px, ey + height, pz, 1, 0.0, 0.05, 0.0, 0.05)
                }
            }
        }

        // === Phase 2: Đỉnh — đổi loài và nổ particles (tick 50) ===
        scheduleTask(50) {
            val ex = CobblemonBridge.getX(pokemon)
            val ey = CobblemonBridge.getY(pokemon)
            val ez = CobblemonBridge.getZ(pokemon)

            // Đổi species/form qua public API — không dùng proxy để tránh crash
            pokemonData.species = newSpecies
            pokemonData.form = newSpecies.standardForm
            pokemonData.initializeMoveset(false)
            pokemonData.updateAspects()

            // Set beam 2 SAU khi đổi species (phòng SpeciesUpdatePacket reset beam client-side)
            CobblemonBridge.setBeamMode(pokemon, 2)

            // Vụ nổ particles lúc biến hình
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ex, ey + 1.0, ez, 2, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(ParticleTypes.END_ROD, ex, ey + 0.5, ez, 80, 1.5, 1.5, 1.5, 0.2)
            level.sendParticles(ParticleTypes.CLOUD, ex, ey + 1.0, ez, 40, 1.0, 0.5, 1.0, 0.04)

            // Âm thanh hoành tráng
            level.playSound(null, CobblemonBridge.getBlockPos(pokemon), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 3.0f, 0.5f)

            // === Phase 3: Tắt beam (tick 80) ===
            scheduleTask(30) {
                CobblemonBridge.setBeamMode(pokemon, 0)
                evolving.remove(pokemonId)
            }
        }
    }
}
