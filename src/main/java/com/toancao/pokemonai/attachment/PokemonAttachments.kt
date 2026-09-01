package com.toancao.pokemonai.attachment

import com.toancao.pokemonai.emotions.EmotionComponent
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.ResourceLocation

object PokemonAttachments {
    val EMOTION: AttachmentType<EmotionComponent> = AttachmentRegistry.createDefaulted(
        ResourceLocation.fromNamespaceAndPath("pokemonai", "emotion")
    ) { EmotionComponent() }

    val EVOLUTION_STATE: AttachmentType<EvolutionStateData> = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath("pokemonai", "evo_state")
    ) { builder -> builder.persistent(EvolutionStateData.CODEC) }

    val HERD_DATA: AttachmentType<com.toancao.pokemonai.spawner.hierarchy.HerdHierarchyData> = AttachmentRegistry.create(
        ResourceLocation.fromNamespaceAndPath("pokemonai", "herd_data")
    ) { builder -> builder.persistent(com.toancao.pokemonai.spawner.hierarchy.HerdHierarchyData.CODEC) }

    fun register() {
        // Triggers static initializers
    }
}
