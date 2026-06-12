package com.toancao.pokemonai.behaviors;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.ai.goal.Goal;

@FunctionalInterface
public interface PokemonBehavior {
    Goal create(PokemonEntity entity);
}
