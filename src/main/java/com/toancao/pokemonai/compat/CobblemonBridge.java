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

    public static void setPokemonData(PokemonEntity entity, Pokemon pokemon) {
        entity.setPokemon(pokemon);
    }

    public static void setBeamMode(PokemonEntity entity, int mode) {
        entity.setBeamMode(mode);
    }

    public static java.util.UUID getEntityUUID(PokemonEntity entity) { return entity.getUUID(); }
    public static net.minecraft.world.level.Level getLevel(PokemonEntity entity) { return entity.level(); }

    public static PokemonEntity createEntity(com.cobblemon.mod.common.api.pokemon.PokemonProperties props, net.minecraft.server.level.ServerLevel level) {
        return (PokemonEntity) props.createEntity(level);
    }
    public static void setPos(PokemonEntity entity, double x, double y, double z) {
        entity.setPos(x, y, z);
    }
    public static void addTag(PokemonEntity entity, String tag) {
        entity.addTag(tag);
    }
    public static void setFlyingFlag(PokemonEntity entity, boolean value) {
        entity.setFlying(value);
    }

}
