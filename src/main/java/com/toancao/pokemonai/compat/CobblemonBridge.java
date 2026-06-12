package com.toancao.pokemonai.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

/**
 * Java bridge for accessing Cobblemon PokemonEntity properties.
 * This avoids Kotlin compiler errors caused by PokemonEntity's unresolved
 * intermediary MC superclasses (class_1316, class_1471, class_5147).
 * All PokemonEntity access from Kotlin should go through this class.
 */
public class CobblemonBridge {

    public static Pokemon getPokemonData(PokemonEntity entity) {
        return entity.getPokemon();
    }

    public static boolean isWild(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        return pokemon.getOwnerUUID() == null && pokemon.getStoreCoordinates().get() == null;
    }

    public static String getSpeciesName(PokemonEntity entity) {
        return entity.getPokemon().getSpecies().getName().toLowerCase();
    }

    public static boolean checkIsPokemonEntity(Object obj) {
        return obj instanceof PokemonEntity;
    }

    public static PokemonEntity castToPokemonEntity(Object obj) {
        return (PokemonEntity) obj;
    }
}
