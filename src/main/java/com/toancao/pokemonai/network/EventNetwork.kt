package com.toancao.pokemonai.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import com.toancao.pokemonai.events.DragonGateEvent
import com.toancao.pokemonai.events.NoticeEventManager

data class SyncEventDataPacket(val events: List<NoticeEventManager.NoticeEvent>) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SyncEventDataPacket>(ResourceLocation.fromNamespaceAndPath("tcpoke_reborn", "sync_event_data"))
        
        val CODEC: StreamCodec<FriendlyByteBuf, SyncEventDataPacket> = CustomPacketPayload.codec(
            { payload: SyncEventDataPacket, buf: FriendlyByteBuf ->
                buf.writeInt(payload.events.size)
                for (event in payload.events) {
                    buf.writeUtf(event.title)
                    buf.writeUtf(event.subtitle)
                    buf.writeUtf(event.desc)
                    buf.writeLong(event.remainingTicks)
                }
            },
            { buf: FriendlyByteBuf ->
                val size = buf.readInt()
                val events = mutableListOf<NoticeEventManager.NoticeEvent>()
                for (i in 0 until size) {
                    events.add(
                        NoticeEventManager.NoticeEvent(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readLong()
                        )
                    )
                }
                SyncEventDataPacket(events)
            }
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return ID
    }
}

object EventNetwork {
    fun registerPayloads() {
        PayloadTypeRegistry.playS2C().register(SyncEventDataPacket.ID, SyncEventDataPacket.CODEC)
    }

    fun sendEventDataToClient(player: ServerPlayer) {
        val level = player.serverLevel()
        val events = NoticeEventManager.getAllEvents(level)
        ServerPlayNetworking.send(player, SyncEventDataPacket(events))
    }
}
