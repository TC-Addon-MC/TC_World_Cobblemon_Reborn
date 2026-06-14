package com.toancao.pokemonai.blocks

import com.toancao.pokemonai.registry.BlockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor

class TcTopBottomBlock : Block(
    BlockBehaviour.Properties.of()
        .mapColor(MapColor.NONE)
        .noCollission()
        .noOcclusion()
), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return BlockRegistry.TC_TOP_BOTTOM_BLOCK_ENTITY.create(pos, state)
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (blockEntityType === BlockRegistry.TC_TOP_BOTTOM_BLOCK_ENTITY) {
            BlockEntityTicker { l, p, s, e -> com.toancao.pokemonai.blocks.entity.TcTopBottomBlockEntity.tick(l, p, s, e as com.toancao.pokemonai.blocks.entity.TcTopBottomBlockEntity) }
        } else null
    }
}
