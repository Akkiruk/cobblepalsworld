package com.cobblepalsworld.gui.router.panel

import net.minecraft.client.gui.DrawContext

internal interface CommandPostPanel {
    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float)
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean = false
    fun onShown(previousMode: com.cobblepalsworld.gui.router.CommandPostMode?) {}
}
