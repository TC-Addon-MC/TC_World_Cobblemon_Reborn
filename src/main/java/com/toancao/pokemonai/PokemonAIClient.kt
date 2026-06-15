package com.toancao.pokemonai

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import com.toancao.pokemonai.network.SyncEventDataPacket
import com.toancao.pokemonai.client.screen.EventNoticeScreen
import net.minecraft.client.Minecraft

class PokemonAIClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncEventDataPacket.ID) { payload, context ->
            context.client().execute {
                Minecraft.getInstance().setScreen(EventNoticeScreen(payload.events))
            }
        }
    }
}
