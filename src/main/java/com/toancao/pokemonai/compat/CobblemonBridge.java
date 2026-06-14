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

    public static void setDummyOwner(Pokemon pokemon) {
        try {
            // Sử dụng Reflection để can thiệp vào backing field 'ownerUUID'
            java.lang.reflect.Field field = pokemon.getClass().getDeclaredField("ownerUUID");
            field.setAccessible(true);
            field.set(pokemon, java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isDummyOwner(Pokemon pokemon) {
        java.util.UUID owner = pokemon.getOwnerUUID();
        return owner != null && owner.toString().equals("00000000-0000-0000-0000-000000000000");
    }

    public static void clearOwner(Pokemon pokemon) {
        try {
            java.lang.reflect.Field field = pokemon.getClass().getDeclaredField("ownerUUID");
            field.setAccessible(true);
            field.set(pokemon, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double getX(PokemonEntity entity) { return entity.getX(); }
    public static double getY(PokemonEntity entity) { return entity.getY(); }
    public static double getZ(PokemonEntity entity) { return entity.getZ(); }
    public static net.minecraft.core.BlockPos getBlockPos(PokemonEntity entity) { return entity.blockPosition(); }
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
}
