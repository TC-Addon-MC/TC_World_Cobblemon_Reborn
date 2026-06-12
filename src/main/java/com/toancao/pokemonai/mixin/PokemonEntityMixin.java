package com.toancao.pokemonai.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.toancao.pokemonai.compat.CobblemonBridge;
import com.toancao.pokemonai.registry.BehaviorRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class PokemonEntityMixin extends LivingEntity {

    protected PokemonEntityMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow @Final public GoalSelector goalSelector;

    @org.spongepowered.asm.mixin.Unique
    private boolean pokemonAi$customGoalsAdded = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (!this.pokemonAi$customGoalsAdded) {
            this.pokemonAi$customGoalsAdded = true;
            if ((Object) this instanceof PokemonEntity pokemonEntity) {
                if (pokemonEntity.getPokemon() != null) {
                    String species = CobblemonBridge.getSpeciesName(pokemonEntity);
                    for (BehaviorRegistry.Entry entry : BehaviorRegistry.getGoals(species)) {
                        this.goalSelector.addGoal(entry.getPriority(), entry.getFactory().create(pokemonEntity));
                    }
                } else {
                    // Try again next tick
                    this.pokemonAi$customGoalsAdded = false;
                }
            }
        }
    }
}
