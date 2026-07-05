@file:Suppress("INACCESSIBLE_TYPE")
package com.toancao.pokemonai.evolution

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.toancao.pokemonai.compat.CobblemonBridge
import com.toancao.pokemonai.utils.EntityUtils
import com.toancao.pokemonai.utils.EvolutionEffectUtils
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
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

        // Gọi API Event
        val allow = com.toancao.pokemonai.api.PokemonAIEvents.BEFORE_FORCE_EVOLVE.invoker().onBeforeForceEvolve(pokemon, targetSpecies)
        if (!allow) return

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

        EvolutionEffectUtils.playEvolutionSequence(
            pokemon = pokemon,
            level = level,
            onEvolve = {
                // Đổi species/form qua public API — không dùng proxy để tránh crash
                pokemonData.species = newSpecies
                pokemonData.form = newSpecies.standardForm
                pokemonData.initializeMoveset(false)
                pokemonData.updateAspects()
            },
            onComplete = {
                evolving.remove(pokemonId)
                com.toancao.pokemonai.api.PokemonAIEvents.AFTER_FORCE_EVOLVE.invoker().onAfterForceEvolve(pokemon, targetSpecies)
            }
        )
    }
}
