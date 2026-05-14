package com.cobblepalsworld.visual

import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.tag.TagRegistry
import com.cobblepalsworld.tag.TagType
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import kotlin.math.sin

object WorkerOverlayRenderer {
    private const val ENTRY_TTL_TICKS = 60L
    private const val MAX_RENDER_DISTANCE_SQ = 72.0 * 72.0

    private val overlays = linkedMapOf<Int, OverlayEntry>()
    private var worldKey: String? = null

    fun replacePastureVisuals(pasturePos: BlockPos, visuals: List<CobblePalsNetworking.WorkerVisualSnapshot>) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        syncWorld(world.registryKey.value.toString())

        val pasture = pasturePos.toImmutable()
        val now = world.time
        overlays.entries.removeIf { it.value.pasturePos == pasture }
        visuals.forEach { snapshot ->
            overlays[snapshot.entityId] = OverlayEntry(pasture, snapshot, now)
        }
    }

    fun render(matrices: MatrixStack) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: run {
            overlays.clear()
            worldKey = null
            return
        }

        syncWorld(world.registryKey.value.toString())
        prune(world.time)
        if (overlays.isEmpty()) return

        val cameraPos = client.gameRenderer.camera.pos
        val vertexConsumers = client.bufferBuilders.entityVertexConsumers

        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()

        matrices.push()
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z)

        overlays.values.forEach { entry ->
            val entity = world.getEntityById(entry.snapshot.entityId) ?: return@forEach
            if (entity.isRemoved) return@forEach
            if (entity.squaredDistanceTo(cameraPos.x, cameraPos.y, cameraPos.z) > MAX_RENDER_DISTANCE_SQ) {
                return@forEach
            }

            val bob = sin((world.time + entity.id) * 0.18).toDouble() * 0.045
            renderHalo(matrices, entity.boundingBox, entry.snapshot.phase(), entry.snapshot.hasCargo(), bob)
            renderIcons(
                client = client,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
                snapshot = entry.snapshot,
                x = entity.x,
                y = entity.boundingBox.maxY + 0.65 + bob,
                z = entity.z,
                seed = entity.id
            )
        }

        vertexConsumers.draw()
        matrices.pop()

        RenderSystem.enableCull()
        RenderSystem.disableBlend()
    }

    private fun renderHalo(
        matrices: MatrixStack,
        entityBox: Box,
        phase: WorkerPhase,
        hasCargo: Boolean,
        bob: Double
    ) {
        val color = phaseColor(phase, hasCargo)
        val centerX = (entityBox.minX + entityBox.maxX) / 2.0
        val centerZ = (entityBox.minZ + entityBox.maxZ) / 2.0
        val haloY = entityBox.maxY + 0.18 + bob
        val haloBox = Box(
            centerX - 0.23,
            haloY,
            centerZ - 0.23,
            centerX + 0.23,
            haloY + 0.18,
            centerZ + 0.23
        )

        val matrix = matrices.peek().positionMatrix
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        TagHighlightRenderer.drawFill(matrix, haloBox, r, g, b, if (hasCargo) 74 else 52)
        TagHighlightRenderer.drawOutline(matrix, haloBox, r, g, b, 210)
    }

    private fun renderIcons(
        client: MinecraftClient,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider.Immediate,
        snapshot: CobblePalsNetworking.WorkerVisualSnapshot,
        x: Double,
        y: Double,
        z: Double,
        seed: Int
    ) {
        resolveTagStack(snapshot.tagTypeId)?.let { tagStack ->
            renderFloatingStack(client, matrices, vertexConsumers, tagStack, x, y, z, 0.45f, seed)
        }

        if (!snapshot.hasCargo()) return

        resolveCargoStack(snapshot.primaryCarriedItemId!!, snapshot.carriedItemCount)?.let { cargoStack ->
            renderFloatingStack(client, matrices, vertexConsumers, cargoStack, x, y - 0.28, z, 0.34f, seed * 31)
            if (snapshot.carriedItemCount > 1) {
                drawCount(client, matrices, vertexConsumers, snapshot.carriedItemCount.toString(), x, y - 0.51, z)
            }
        }
    }

    private fun renderFloatingStack(
        client: MinecraftClient,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider.Immediate,
        stack: ItemStack,
        x: Double,
        y: Double,
        z: Double,
        scale: Float,
        seed: Int
    ) {
        val world = client.world ?: return
        matrices.push()
        matrices.translate(x, y, z)
        matrices.multiply(client.entityRenderDispatcher.rotation)
        matrices.scale(scale, -scale, scale)
        client.itemRenderer.renderItem(
            stack,
            net.minecraft.client.render.model.json.ModelTransformationMode.FIXED,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            OverlayTexture.DEFAULT_UV,
            matrices,
            vertexConsumers,
            world,
            seed
        )
        matrices.pop()
    }

    private fun drawCount(
        client: MinecraftClient,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider.Immediate,
        countText: String,
        x: Double,
        y: Double,
        z: Double
    ) {
        val halfWidth = client.textRenderer.getWidth(countText) / 2f
        matrices.push()
        matrices.translate(x, y, z)
        matrices.multiply(client.entityRenderDispatcher.rotation)
        matrices.scale(0.025f, -0.025f, 0.025f)
        client.textRenderer.draw(
            countText,
            -halfWidth,
            0f,
            0xF6F3E8.toInt(),
            false,
            matrices.peek().positionMatrix,
            vertexConsumers,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0x66000000,
            LightmapTextureManager.MAX_LIGHT_COORDINATE
        )
        matrices.pop()
    }

    private fun resolveTagStack(tagTypeId: String): ItemStack? {
        val type = TagType.fromId(tagTypeId) ?: return null
        val item = TagRegistry.getItem(type) ?: return null
        return ItemStack(item)
    }

    private fun resolveCargoStack(itemId: String, carriedItemCount: Int): ItemStack? {
        val identifier = runCatching { Identifier.of(itemId) }.getOrNull() ?: return null
        val item = Registries.ITEM.get(identifier)
        if (item == Items.AIR) return null
        return ItemStack(item, carriedItemCount.coerceAtLeast(1).coerceAtMost(item.maxCount))
    }

    private fun phaseColor(phase: WorkerPhase, hasCargo: Boolean): Int {
        return when (phase) {
            WorkerPhase.NAVIGATING -> 0x4F8BFF
            WorkerPhase.ARRIVING -> 0xF3C969
            WorkerPhase.WORKING -> 0x56CF7A
            WorkerPhase.DEPOSITING -> 0x33C7C4
            WorkerPhase.IDLE -> if (hasCargo) 0xD9A84F else 0x8D96A1
        }
    }

    private fun CobblePalsNetworking.WorkerVisualSnapshot.phase(): WorkerPhase {
        return WorkerPhase.entries.getOrElse(phaseOrdinal) { WorkerPhase.IDLE }
    }

    private fun CobblePalsNetworking.WorkerVisualSnapshot.hasCargo(): Boolean {
        return !primaryCarriedItemId.isNullOrBlank() && carriedItemCount > 0
    }

    private fun syncWorld(currentWorldKey: String) {
        if (worldKey == currentWorldKey) return
        worldKey = currentWorldKey
        overlays.clear()
    }

    private fun prune(now: Long) {
        overlays.entries.removeIf { (_, entry) -> now - entry.updatedAt > ENTRY_TTL_TICKS }
    }

    private data class OverlayEntry(
        val pasturePos: BlockPos,
        val snapshot: CobblePalsNetworking.WorkerVisualSnapshot,
        val updatedAt: Long
    )
}