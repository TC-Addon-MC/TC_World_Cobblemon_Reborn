package com.toancao.pokemonai.blocks

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

class DragonGateWaypointBlockItem(block: Block, properties: Properties) : BlockItem(block, properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val player = context.player
        val state = level.getBlockState(pos)

        // Can link FROM BottomBlock OR another WaypointBlock
        if ((state.block is DragonGateBottomBlock || state.block is DragonGateWaypointBlock) && player?.isCrouching == true) {
            if (!level.isClientSide) {
                val stack = context.itemInHand
                val customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                val tag = customData.copyTag()
                tag.putInt("LinkedX", pos.x)
                tag.putInt("LinkedY", pos.y)
                tag.putInt("LinkedZ", pos.z)
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
                
                val blockName = if (state.block is DragonGateBottomBlock) "Bottom Gate" else "Waypoint"
                player.displayClientMessage(Component.literal("§a[Dragon Gate] §fLinked to $blockName at [${pos.x}, ${pos.y}, ${pos.z}]"), true)
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f)
            }
            return InteractionResult.SUCCESS
        }

        return super.useOn(context)
    }

    override fun updateCustomBlockEntityTag(pos: BlockPos, level: Level, player: net.minecraft.world.entity.player.Player?, stack: ItemStack, state: BlockState): Boolean {
        // When this waypoint block is PLACED, link it back to the previous block
        val data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
        val tag = data?.copyTag()
        if (tag != null && tag.contains("LinkedX")) {
            val lx = tag.getInt("LinkedX")
            val ly = tag.getInt("LinkedY")
            val lz = tag.getInt("LinkedZ")
            val prevPos = BlockPos(lx, ly, lz)
            
            val be = level.getBlockEntity(prevPos)
            if (be is com.toancao.pokemonai.blocks.entity.DragonGateBottomBlockEntity) {
                be.updateTopPos(pos)
                if (!level.isClientSide) {
                    player?.displayClientMessage(Component.literal("§a[Dragon Gate] §fSuccessfully linked Waypoint to Bottom Block!"), true)
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f)
                }
            } else if (be is com.toancao.pokemonai.blocks.entity.DragonGateWaypointBlockEntity) {
                be.updateNextPos(pos)
                if (!level.isClientSide) {
                    player?.displayClientMessage(Component.literal("§a[Dragon Gate] §fSuccessfully linked Waypoint to previous Waypoint!"), true)
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f)
                }
            } else {
                if (!level.isClientSide) {
                    player?.displayClientMessage(Component.literal("§c[Dragon Gate] §fPrevious Block at [$lx, $ly, $lz] not found!"), true)
                }
            }
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state)
    }
}
