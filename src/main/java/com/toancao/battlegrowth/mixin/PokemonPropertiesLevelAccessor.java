package com.toancao.battlegrowth.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PokemonProperties.class)
public interface PokemonPropertiesLevelAccessor {

    @Accessor(value = "level", remap = false)
    Integer getLevel();

    @Accessor(value = "level", remap = false)
    void setLevel(Integer level);
}