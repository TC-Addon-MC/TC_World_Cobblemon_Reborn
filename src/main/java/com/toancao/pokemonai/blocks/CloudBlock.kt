package com.toancao.pokemonai.blocks

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.Shapes

class CloudBlock : Block(BlockBehaviour.Properties.of().noLootTable().noOcclusion().air()) {

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return Shapes.block() // Mang đặc tính rắn để đứng lên được
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return Shapes.block()
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 20) // 1 second = 20 ticks
        }
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (!level.isClientSide) return
        
        // Quét tìm người chơi gần nhất trong bán kính 30 block
        val player = level.getNearestPlayer(pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5, 30.0, false)
        if (player != null) {
            val mainItem = player.mainHandItem
            val offItem = player.offhandItem
            val cloudItem = com.toancao.pokemonai.registry.BlockRegistry.CLOUD_BLOCK.asItem()
            
            // Nếu người chơi đang cầm item Cloud Block thì phát hạt mây ra
            if (mainItem.`is`(cloudItem) || offItem.`is`(cloudItem)) {
                // Tăng số lượng particle để dễ nhận biết hơn (spawn 3 hạt mỗi tick thay vì thỉnh thoảng 1 hạt)
                for (i in 0..2) {
                    level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.CLOUD,
                        pos.x + random.nextDouble(),
                        pos.y + random.nextDouble(),
                        pos.z + random.nextDouble(),
                        0.0, 0.0, 0.0
                    )
                }
            }
        }
    }
}
