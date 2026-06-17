package com.toancao.pokemonai.client.screen

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import kotlin.math.max
import com.toancao.pokemonai.events.NoticeEventManager

@Environment(EnvType.CLIENT)
class EventNoticeScreen(initialEvents: List<NoticeEventManager.NoticeEvent>) : Screen(Component.literal("Event Notice")) {

    companion object {
        val POKEDEX_BASE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/pokedex_base_red.png")
        val POKEDEX_FRAME = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/pokedex_screen.png")
        
        const val BASE_WIDTH = 345
        const val BASE_HEIGHT = 207
    }

    private var selectedEventIndex = 0
    private var hasPlayedOpenSound = false
    private var scrollOffset = 0.0
    private val mutableEvents = initialEvents.map { it.copy() }.toMutableList()

    override fun init() {
        super.init()

        if (!hasPlayedOpenSound) {
            hasPlayedOpenSound = true
            val openSound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", "item.pokedex.open"))
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(openSound, 1.0f, 1.0f))
        }
    }
    
    override fun tick() {
        super.tick()
        for (i in mutableEvents.indices) {
            if (mutableEvents[i].remainingTicks > 0) {
                mutableEvents[i] = mutableEvents[i].copy(remainingTicks = mutableEvents[i].remainingTicks - 1)
            }
        }
    }

    private fun formatTime(ticks: Long): String {
        val totalSeconds = ticks / 20
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        
        guiGraphics.blit(POKEDEX_FRAME, x, y, 0f, 0f, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT)
        
        val font = Minecraft.getInstance().font
        
        guiGraphics.drawString(font, net.minecraft.client.resources.language.I18n.get("gui.tc_reborn.event.list"), x + 26, y + 14, 0xFFFFFF, true)
        guiGraphics.drawString(font, net.minecraft.client.resources.language.I18n.get("gui.tc_reborn.event.details"), x + 180, y + 14, 0xFFFFFF, true)
        
        val listX = x + 26
        val listY = y + 28
        val listWidth = 145
        val listHeight = 155
        
        guiGraphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight)
        
        var currentY = listY - scrollOffset.toInt()
        val btnHeight = 28
        val gap = 2
        
        for (i in mutableEvents.indices) {
            val ev = mutableEvents[i]
            val isSelected = selectedEventIndex == i
            val isHovered = mouseX >= listX && mouseX <= listX + listWidth && 
                            mouseY >= currentY && mouseY <= currentY + btnHeight &&
                            mouseY >= listY && mouseY <= listY + listHeight
            
            val bgColor = if (isSelected) 0xFF186F80.toInt() else if (isHovered) 0xFF205565.toInt() else 0xFF235C6E.toInt()
            val borderColor = if (isSelected || isHovered) 0xFF3DB7C9.toInt() else 0xFF1B4E5E.toInt()
            
            guiGraphics.fill(listX, currentY, listX + listWidth, currentY + btnHeight, bgColor)
            guiGraphics.fill(listX, currentY, listX + listWidth, currentY + 1, borderColor) 
            guiGraphics.fill(listX, currentY + btnHeight - 1, listX + listWidth, currentY + btnHeight, borderColor) 
            guiGraphics.fill(listX, currentY, listX + 1, currentY + btnHeight, borderColor) 
            guiGraphics.fill(listX + listWidth - 1, currentY, listX + listWidth, currentY + btnHeight, borderColor) 
            
            if (isSelected) {
                guiGraphics.drawString(font, ">", listX - 8, currentY + 10, 0xFFFFFF, true)
            }
            
            com.mojang.blaze3d.systems.RenderSystem.enableBlend()
            guiGraphics.blit(ev.icon, listX + 5, currentY + 4, 0f, 0f, 20, 20, 20, 20)
            com.mojang.blaze3d.systems.RenderSystem.disableBlend()
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.9f, 0.9f, 0.9f)
            guiGraphics.drawString(font, net.minecraft.client.resources.language.I18n.get(ev.title), ((listX + 30) / 0.9f).toInt(), ((currentY + 5) / 0.9f).toInt(), 0xFFFFFF, false)
            guiGraphics.pose().popPose()
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.8f, 0.8f, 0.8f)
            val listSubtitle = "${net.minecraft.client.resources.language.I18n.get(ev.subtitle)} ${formatTime(ev.remainingTicks)}"
            guiGraphics.drawString(font, listSubtitle, ((listX + 30) / 0.8f).toInt(), ((currentY + 16) / 0.8f).toInt(), 0xAAAAAA, false)
            guiGraphics.pose().popPose()
            
            currentY += btnHeight + gap
        }
        
        guiGraphics.disableScissor()
        
        val rightX = x + 180
        val rightY = y + 28
        val rightWidth = 140
        
        guiGraphics.fill(rightX, rightY, rightX + rightWidth, rightY + 60, 0xFF1A1A1A.toInt())
        guiGraphics.drawString(font, "<", rightX + 5, rightY + 25, 0x00FFFF, true)
        guiGraphics.drawString(font, ">", rightX + rightWidth - 10, rightY + 25, 0x00FFFF, true)
        
        if (mutableEvents.isNotEmpty() && selectedEventIndex in mutableEvents.indices) {
            val selectedEv = mutableEvents[selectedEventIndex]
            
            // Render event image player
            guiGraphics.pose().pushPose()
            
            val playerWidth = 136
            val playerHeight = 56
            val playerX = rightX + (rightWidth - playerWidth) / 2
            val playerY = rightY + (60 - playerHeight) / 2
            
            com.mojang.blaze3d.systems.RenderSystem.enableBlend()
            guiGraphics.blit(selectedEv.image, playerX, playerY, 0f, 0f, playerWidth, playerHeight, playerWidth, playerHeight)
            com.mojang.blaze3d.systems.RenderSystem.disableBlend()
            
            guiGraphics.pose().popPose()
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.9f, 0.9f, 0.9f)
            guiGraphics.drawString(font, net.minecraft.client.resources.language.I18n.get(selectedEv.title), ((rightX) / 0.9f).toInt(), ((rightY + 65) / 0.9f).toInt(), 0xFFFFFF, true)
            guiGraphics.pose().popPose()
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.8f, 0.8f, 0.8f)
            val descParts = selectedEv.desc.split("|")
            val translatedDesc = if (descParts.size > 1) {
                net.minecraft.client.resources.language.I18n.get(descParts[0], *descParts.drop(1).toTypedArray())
            } else {
                net.minecraft.client.resources.language.I18n.get(descParts[0])
            }
            val descLines = translatedDesc.split("\\n", "\n")
            var lineYOffset = 0
            for (i in descLines.indices) {
                val currentLine = descLines[i]
                if (currentLine.isNotEmpty()) {
                    guiGraphics.drawString(font, currentLine, (rightX / 0.8f).toInt(), ((rightY + 76 + lineYOffset * 10) / 0.8f).toInt(), 0xAAAAAA, false)
                    lineYOffset++
                }
            }
            guiGraphics.pose().popPose()
            
            guiGraphics.drawString(font, net.minecraft.client.resources.language.I18n.get(selectedEv.subtitle), rightX, rightY + 110, 0xFFFFFF, true)
            
            val boxWidth = 35
            val boxHeight = 28
            val timerGap = 8
            val timerY = rightY + 122
            
            val totalSeconds = selectedEv.remainingTicks / 20
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            drawTimerBox(guiGraphics, font, rightX, timerY, boxWidth, boxHeight, String.format("%02d", hours), net.minecraft.client.resources.language.I18n.get("gui.tc_reborn.event.hours"))
            guiGraphics.drawString(font, ":", rightX + boxWidth + 2, timerY + 8, 0xFFFFFF, true)
            drawTimerBox(guiGraphics, font, rightX + boxWidth + timerGap, timerY, boxWidth, boxHeight, String.format("%02d", minutes), net.minecraft.client.resources.language.I18n.get("gui.tc_reborn.event.minutes"))
            guiGraphics.drawString(font, ":", rightX + 2 * boxWidth + timerGap + 2, timerY + 8, 0xFFFFFF, true)
            drawTimerBox(guiGraphics, font, rightX + 2 * (boxWidth + timerGap), timerY, boxWidth, boxHeight, String.format("%02d", seconds), net.minecraft.client.resources.language.I18n.get("gui.tc_reborn.event.seconds"))
        } else {
            guiGraphics.drawString(font, net.minecraft.client.resources.language.I18n.get("gui.tc_reborn.event.no_events"), rightX, rightY + 65, 0xAAAAAA, false)
        }
        
        guiGraphics.blit(POKEDEX_BASE, x, y, 0f, 0f, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT)
        
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
    
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        val listX = x + 26
        val listY = y + 28
        val listWidth = 145
        val listHeight = 155

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            val clickY = mouseY - listY + scrollOffset
            val index = (clickY / 30).toInt()
            if (index in mutableEvents.indices) {
                selectedEventIndex = index
                val clickSound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", "item.pokedex.click"))
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(clickSound, 1.0f, 1.0f))
                return true
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val maxScroll = max(0.0, (mutableEvents.size * 30.0) - 155.0)
        scrollOffset -= scrollY * 15.0
        scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)
        return true
    }

    private fun drawTimerBox(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font, x: Int, y: Int, w: Int, h: Int, time: String, label: String) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF235C6E.toInt())
        guiGraphics.fill(x, y, x + w, y + 1, 0xFF3DB7C9.toInt()) // top
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFF3DB7C9.toInt()) // bottom
        guiGraphics.fill(x, y, x + 1, y + h, 0xFF3DB7C9.toInt()) // left
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFF3DB7C9.toInt()) // right
        
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x + w / 2 - font.width(time)).toDouble(), (y + 4).toDouble(), 0.0)
        guiGraphics.pose().scale(2.0f, 2.0f, 2.0f)
        guiGraphics.drawString(font, time, 0, 0, 0x00FFFF, true)
        guiGraphics.pose().popPose()
        
        guiGraphics.pose().pushPose()
        guiGraphics.pose().scale(0.8f, 0.8f, 0.8f)
        guiGraphics.drawString(font, label, ((x + w / 2 - font.width(label) * 0.4f) / 0.8f).toInt(), ((y + 20) / 0.8f).toInt(), 0xAAAAAA, false)
        guiGraphics.pose().popPose()
    }
    
    override fun isPauseScreen(): Boolean {
        return false
    }
}

