package com.toancao.pokemonai.utils

import net.minecraft.world.entity.Entity

// NOTE: NbtUtils is intentionally limited.
// writeCustomDataToNbt/readCustomDataFromNbt are protected in Entity — cannot be called from external Kotlin code.
// Use Fabric Attachment API (PokemonAttachments) for persistent data instead.
// This object is kept as a placeholder for any future safe NBT operations.
object NbtUtils {
    // Use PokemonAttachments.EMOTION / EVOLUTION_STATE for persisting data.
    // Direct NBT manipulation of Entity requires a Mixin to access protected methods.
}
