package com.toancao.pokemonai.behaviors.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.block.Block

object BlockBreakHandler {

    private val BREAKABLE_TAG: TagKey<Block> = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("tc_reborn", "tauros_breakable")
    )

    /**
     * Phá hủy các khối cản hợp lệ trong phạm vi hộp và trả về số lượng khối đã phá
     */
    fun tryBreakBlocksInBox(level: ServerLevel, minPos: BlockPos, maxPos: BlockPos): Int {
        if (!level.gameRules.getBoolean(GameRules.RULE_MOBGRIEFING)) return 0

        var brokenCount = 0
        for (x in minPos.x..maxPos.x) {
            for (y in minPos.y..maxPos.y) {
                for (z in minPos.z..maxPos.z) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)

                    if (state.`is`(BREAKABLE_TAG)) {
                        level.destroyBlock(pos, false)
                        brokenCount++
                    }
                }
            }
        }
        return brokenCount
    }
}
