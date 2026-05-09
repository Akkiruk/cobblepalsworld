package com.cobblepalsworld.visual

import com.cobblepalsworld.tag.TagItem
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import org.joml.Matrix4f

object TagHighlightRenderer {

    fun render(matrices: MatrixStack) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val camera = client.gameRenderer.camera

        val entries = mutableListOf<HighlightEntry>()
        for (stack in listOf(player.mainHandStack, player.offHandStack)) {
            val item = stack.item as? TagItem ?: continue
            val pos = TagItem.getBoundPos(stack) ?: continue
            val c = item.tagType.color.colorValue ?: continue
            entries += HighlightEntry(Box(pos).expand(0.002), c)
        }
        if (entries.isEmpty()) return

        val cam = camera.pos
        matrices.push()
        matrices.translate(-cam.x, -cam.y, -cam.z)

        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()

        for ((box, color) in entries) {
            val r = (color shr 16 and 0xFF)
            val g = (color shr 8 and 0xFF)
            val b = (color and 0xFF)
            val matrix = matrices.peek().positionMatrix
            drawFill(matrix, box, r, g, b, 64)
            drawOutline(matrix, box, r, g, b, 200)
        }

        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.enableDepthTest()
        matrices.pop()
    }

    private fun drawFill(matrix: Matrix4f, box: Box, r: Int, g: Int, b: Int, a: Int) {
        val buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        val x0 = box.minX.toFloat(); val y0 = box.minY.toFloat(); val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat(); val y1 = box.maxY.toFloat(); val z1 = box.maxZ.toFloat()

        // Top
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a)
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a)
        // Bottom
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a)
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a)
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a)
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a)
        // North (-Z)
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a)
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a)
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a)
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a)
        // South (+Z)
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a)
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a)
        // West (-X)
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a)
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a)
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a)
        // East (+X)
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a)
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a)
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a)

        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        BufferRenderer.drawWithGlobalProgram(buf.end())
    }

    private fun drawOutline(matrix: Matrix4f, box: Box, r: Int, g: Int, b: Int, a: Int) {
        val buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR)
        val x0 = box.minX.toFloat(); val y0 = box.minY.toFloat(); val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat(); val y1 = box.maxY.toFloat(); val z1 = box.maxZ.toFloat()

        // Bottom edges
        edge(buf, matrix, x0, y0, z0, x1, y0, z0, r, g, b, a)
        edge(buf, matrix, x1, y0, z0, x1, y0, z1, r, g, b, a)
        edge(buf, matrix, x1, y0, z1, x0, y0, z1, r, g, b, a)
        edge(buf, matrix, x0, y0, z1, x0, y0, z0, r, g, b, a)
        // Top edges
        edge(buf, matrix, x0, y1, z0, x1, y1, z0, r, g, b, a)
        edge(buf, matrix, x1, y1, z0, x1, y1, z1, r, g, b, a)
        edge(buf, matrix, x1, y1, z1, x0, y1, z1, r, g, b, a)
        edge(buf, matrix, x0, y1, z1, x0, y1, z0, r, g, b, a)
        // Vertical edges
        edge(buf, matrix, x0, y0, z0, x0, y1, z0, r, g, b, a)
        edge(buf, matrix, x1, y0, z0, x1, y1, z0, r, g, b, a)
        edge(buf, matrix, x1, y0, z1, x1, y1, z1, r, g, b, a)
        edge(buf, matrix, x0, y0, z1, x0, y1, z1, r, g, b, a)

        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        RenderSystem.lineWidth(3.0f)
        BufferRenderer.drawWithGlobalProgram(buf.end())
        RenderSystem.lineWidth(1.0f)
    }

    private fun edge(buf: BufferBuilder, matrix: Matrix4f,
                     x1: Float, y1: Float, z1: Float,
                     x2: Float, y2: Float, z2: Float,
                     r: Int, g: Int, b: Int, a: Int) {
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a)
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a)
    }

    private data class HighlightEntry(val box: Box, val color: Int)
}
