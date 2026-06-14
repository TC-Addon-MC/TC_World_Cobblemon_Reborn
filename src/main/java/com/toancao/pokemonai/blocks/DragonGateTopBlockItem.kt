package com.toancao.pokemonai.blocks

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

class DragonGateTopBlockItem(block: Block, properties: Properties) : BlockItem(block, properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val player = context.player
        val state = level.getBlockState(pos)

        // Can link FROM BottomBlock OR WaypointBlock
        if ((state.block is DragonGateBottomBlock || state.block is DragonGateWaypointBlock) && player?.isCrouching == true) {
            if (!level.isClientSide) {
                val stack = context.itemInHand
                val customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                val tag = customData.copyTag()
                tag.putInt("LinkedX", pos.x)
                tag.putInt("LinkedY", pos.y)
                tag.putInt("LinkedZ", pos.z)
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
                
                player.displayClientMessage(Component.literal("§a[Dragon Gate] §fLinked to Bottom Gate at [${pos.x}, ${pos.y}, ${pos.z}]"), true)
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f)
            }
            return InteractionResult.SUCCESS
        }

        return super.useOn(context)
    }
}
