package com.toancao.pokemonai.items

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerPlayer
import com.toancao.pokemonai.network.EventNetwork

class EventDeviceItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = player.getItemInHand(usedHand)
        
        if (!level.isClientSide && player is ServerPlayer) {
            // Khi người chơi mở, Server gửi thông tin sự kiện xuống Client
            EventNetwork.sendEventDataToClient(player)
        }
        
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide())
    }

    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }
}
