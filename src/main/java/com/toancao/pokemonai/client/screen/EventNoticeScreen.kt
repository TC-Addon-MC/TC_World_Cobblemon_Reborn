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
        
        guiGraphics.drawString(font, "Event List", x + 26, y + 14, 0xFFFFFF, true)
        guiGraphics.drawString(font, "Event Details", x + 180, y + 14, 0xFFFFFF, true)
        
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
            
            guiGraphics.fill(listX + 5, currentY + 4, listX + 25, currentY + 24, 0xFF444444.toInt())
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.9f, 0.9f, 0.9f)
            guiGraphics.drawString(font, ev.title, ((listX + 30) / 0.9f).toInt(), ((currentY + 5) / 0.9f).toInt(), 0xFFFFFF, false)
            guiGraphics.pose().popPose()
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.8f, 0.8f, 0.8f)
            val listSubtitle = "${ev.subtitle} ${formatTime(ev.remainingTicks)}"
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
        
        guiGraphics.pose().pushPose()
        guiGraphics.pose().scale(0.8f, 0.8f, 0.8f)
        guiGraphics.drawString(font, "Image Placeholder", ((rightX + 30) / 0.8f).toInt(), ((rightY + 26) / 0.8f).toInt(), 0x888888, false)
        guiGraphics.pose().popPose()
        
        if (mutableEvents.isNotEmpty() && selectedEventIndex in mutableEvents.indices) {
            val selectedEv = mutableEvents[selectedEventIndex]
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.9f, 0.9f, 0.9f)
            guiGraphics.drawString(font, selectedEv.title, ((rightX) / 0.9f).toInt(), ((rightY + 65) / 0.9f).toInt(), 0xFFFFFF, true)
            guiGraphics.pose().popPose()
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().scale(0.8f, 0.8f, 0.8f)
            val descLines = selectedEv.desc.split("\n")
            for (i in descLines.indices) {
                guiGraphics.drawString(font, descLines[i], (rightX / 0.8f).toInt(), ((rightY + 76 + i * 10) / 0.8f).toInt(), 0xAAAAAA, false)
            }
            guiGraphics.pose().popPose()
            
            guiGraphics.drawString(font, selectedEv.subtitle, rightX, rightY + 110, 0xFFFFFF, true)
            
            val boxWidth = 35
            val boxHeight = 28
            val timerGap = 8
            val timerY = rightY + 122
            
            val totalSeconds = selectedEv.remainingTicks / 20
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            drawTimerBox(guiGraphics, font, rightX, timerY, boxWidth, boxHeight, String.format("%02d", hours), "Hours")
            guiGraphics.drawString(font, ":", rightX + boxWidth + 2, timerY + 8, 0xFFFFFF, true)
            drawTimerBox(guiGraphics, font, rightX + boxWidth + timerGap, timerY, boxWidth, boxHeight, String.format("%02d", minutes), "Minutes")
            guiGraphics.drawString(font, ":", rightX + 2 * boxWidth + timerGap + 2, timerY + 8, 0xFFFFFF, true)
            drawTimerBox(guiGraphics, font, rightX + 2 * (boxWidth + timerGap), timerY, boxWidth, boxHeight, String.format("%02d", seconds), "Seconds")
        } else {
            guiGraphics.drawString(font, "No Events Available", rightX, rightY + 65, 0xAAAAAA, false)
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

