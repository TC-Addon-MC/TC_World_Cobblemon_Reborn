package com.toancao.pokemonai.evolution

object EvolutionRegistry {
    private val speciesRules = mutableMapOf<String, MutableList<EvolutionRule>>()

    fun register(species: String, rules: List<EvolutionRule>) {
        speciesRules.getOrPut(species.lowercase()) { mutableListOf() }.addAll(rules)
    }

    fun getRules(species: String): List<EvolutionRule> {
        return speciesRules[species.lowercase()] ?: emptyList()
    }

    fun clear() {
        speciesRules.clear()
    }
}



