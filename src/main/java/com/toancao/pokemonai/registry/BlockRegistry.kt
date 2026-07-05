package com.toancao.pokemonai.registry

import com.toancao.pokemonai.blocks.DragonGateBottomBlock
import com.toancao.pokemonai.blocks.DragonGateTopBlock
import com.toancao.pokemonai.blocks.DragonGateWaypointBlock
import com.toancao.pokemonai.blocks.TcTopBottomBlock
import com.toancao.pokemonai.blocks.entity.DragonGateBottomBlockEntity
import com.toancao.pokemonai.blocks.entity.DragonGateWaypointBlockEntity
import com.toancao.pokemonai.blocks.entity.TcTopBottomBlockEntity
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.CreativeModeTab

object BlockRegistry {
    val DRAGON_GATE_BOTTOM_BLOCK = DragonGateBottomBlock()
    val DRAGON_GATE_WAYPOINT_BLOCK = DragonGateWaypointBlock()
    val DRAGON_GATE_TOP_BLOCK = DragonGateTopBlock()
    val TC_TOP_BOTTOM_BLOCK = TcTopBottomBlock()
    val CLOUD_BLOCK = com.toancao.pokemonai.blocks.CloudBlock()
    val EVENT_DEVICE = com.toancao.pokemonai.items.EventDeviceItem(net.minecraft.world.item.Item.Properties().stacksTo(1))

    val TC_REBORN_TAB: CreativeModeTab = FabricItemGroup.builder()
        .icon { ItemStack(EVENT_DEVICE) }
        .title(Component.translatable("itemGroup.tc_reborn"))
        .displayItems { _, entries ->
            entries.accept(EVENT_DEVICE)
        }
        .build()

    val DRAGON_GATE_BOTTOM_BLOCK_ENTITY: BlockEntityType<DragonGateBottomBlockEntity> = BlockEntityType.Builder.of(
        ::DragonGateBottomBlockEntity, DRAGON_GATE_BOTTOM_BLOCK
    ).build(null as com.mojang.datafixers.types.Type<*>?)
    
    val DRAGON_GATE_WAYPOINT_BLOCK_ENTITY: BlockEntityType<DragonGateWaypointBlockEntity> = BlockEntityType.Builder.of(
        ::DragonGateWaypointBlockEntity, DRAGON_GATE_WAYPOINT_BLOCK
    ).build(null as com.mojang.datafixers.types.Type<*>?)

    val DRAGON_GATE_TOP_BLOCK_ENTITY: BlockEntityType<com.toancao.pokemonai.blocks.entity.DragonGateTopBlockEntity> = BlockEntityType.Builder.of(
        { pos, state -> com.toancao.pokemonai.blocks.entity.DragonGateTopBlockEntity(pos, state) }, DRAGON_GATE_TOP_BLOCK
    ).build(null as com.mojang.datafixers.types.Type<*>?)

    val TC_TOP_BOTTOM_BLOCK_ENTITY: BlockEntityType<TcTopBottomBlockEntity> = BlockEntityType.Builder.of(
        ::TcTopBottomBlockEntity, TC_TOP_BOTTOM_BLOCK
    ).build(null as com.mojang.datafixers.types.Type<*>?)

    fun register() {
        registerBlock("dragon_gate_bottom", DRAGON_GATE_BOTTOM_BLOCK)
        registerBlock("dragon_gate_waypoint", DRAGON_GATE_WAYPOINT_BLOCK)
        registerBlock("dragon_gate_top", DRAGON_GATE_TOP_BLOCK)
        registerBlock("tc_top_bottom", TC_TOP_BOTTOM_BLOCK)
        registerBlock("cloud_block", CLOUD_BLOCK)

        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("tc_reborn", "event_device"),
            EVENT_DEVICE
        )

        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("tc_reborn", "dragon_gate_bottom_entity"),
            DRAGON_GATE_BOTTOM_BLOCK_ENTITY
        )
        
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("tc_reborn", "dragon_gate_waypoint_entity"),
            DRAGON_GATE_WAYPOINT_BLOCK_ENTITY
        )

        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("tc_reborn", "dragon_gate_top_entity"),
            DRAGON_GATE_TOP_BLOCK_ENTITY
        )

        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("tc_reborn", "tc_top_bottom_entity"),
            TC_TOP_BOTTOM_BLOCK_ENTITY
        )

        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("tc_reborn", "tc_reborn"),
            TC_REBORN_TAB
        )
    }

    private fun registerBlock(name: String, block: Block) {
        val id = ResourceLocation.fromNamespaceAndPath("tc_reborn", name)
        Registry.register(BuiltInRegistries.BLOCK, id, block)
        
        val blockItem = if (block is DragonGateTopBlock) {
            com.toancao.pokemonai.blocks.DragonGateTopBlockItem(block, Item.Properties())
        } else if (block is DragonGateWaypointBlock) {
            com.toancao.pokemonai.blocks.DragonGateWaypointBlockItem(block, Item.Properties())
        } else {
            BlockItem(block, Item.Properties())
        }
        Registry.register(BuiltInRegistries.ITEM, id, blockItem)
    }
}
