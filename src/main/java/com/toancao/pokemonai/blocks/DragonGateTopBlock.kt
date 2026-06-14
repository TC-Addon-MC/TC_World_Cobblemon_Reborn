package com.toancao.pokemonai.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType

class DragonGateTopBlock : Block(BlockBehaviour.Properties.of().instabreak().noCollission().noOcclusion()), SimpleWaterloggedBlock, EntityBlock {
    
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_TOP_BLOCK_ENTITY.create(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (blockEntityType === com.toancao.pokemonai.registry.BlockRegistry.DRAGON_GATE_TOP_BLOCK_ENTITY) {
            BlockEntityTicker { l, p, s, e -> com.toancao.pokemonai.blocks.entity.DragonGateTopBlockEntity.tick(l, p, s, e as com.toancao.pokemonai.blocks.entity.DragonGateTopBlockEntity) }
        } else null
    }
    companion object {
        val WATERLOGGED: BooleanProperty = BlockStateProperties.WATERLOGGED
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(WATERLOGGED)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return defaultBlockState().setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
    }

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, currentPos: BlockPos, neighborPos: BlockPos): BlockState {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
        val tag = data?.copyTag()
        if (tag != null && tag.contains("LinkedX")) {
            val lx = tag.getInt("LinkedX")
            val ly = tag.getInt("LinkedY")
            val lz = tag.getInt("LinkedZ")
            val bottomPos = BlockPos(lx, ly, lz)
            
            val be = level.getBlockEntity(bottomPos)
            val player = placer as? net.minecraft.world.entity.player.Player
            if (be is com.toancao.pokemonai.blocks.entity.DragonGateBottomBlockEntity) {
                be.updateTopPos(pos)
                if (!level.isClientSide) {
                    player?.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[Dragon Gate] §fSuccessfully linked Top Block to Bottom Block!"), true)
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
                }
            } else if (be is com.toancao.pokemonai.blocks.entity.DragonGateWaypointBlockEntity) {
                be.updateNextPos(pos)
                if (!level.isClientSide) {
                    player?.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[Dragon Gate] §fSuccessfully linked Top Block to Waypoint!"), true)
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f)
                }
            } else {
                if (!level.isClientSide) {
                    player?.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Dragon Gate] §fPrevious Block at [$lx, $ly, $lz] not found!"), true)
                }
            }
        }
    }
}
