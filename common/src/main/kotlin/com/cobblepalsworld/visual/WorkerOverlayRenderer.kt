package com.cobblepalsworld.visual

import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.tag.TagRegistry
import com.cobblepalsworld.tag.TagType
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object WorkerOverlayRenderer {
    private const val ENTRY_TTL_TICKS = 60L
    private const val MAX_RENDER_DISTANCE_SQ = 72.0 * 72.0

    private val overlays = linkedMapOf<Int, OverlayEntry>()
    private var worldKey: String? = null

    fun replaceWorksiteVisuals(worksitePos: BlockPos, visuals: List<CobblePalsNetworking.WorkerVisualSnapshot>) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        syncWorld(world.registryKey.value.toString())

        val worksite = worksitePos.toImmutable()
        val now = world.time
        overlays.entries.removeIf { it.value.worksitePos == worksite }
        visuals.forEach { snapshot ->
            overlays[snapshot.entityId] = OverlayEntry(worksite, snapshot, now)
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

        overlays.values.groupBy { it.worksitePos }.values.forEach { worksiteEntries ->
            worksiteEntries.forEachIndexed { index, entry ->
                val entity = world.getEntityById(entry.snapshot.entityId) ?: return@forEachIndexed
                if (entity.isRemoved) return@forEachIndexed
                if (entity.squaredDistanceTo(cameraPos.x, cameraPos.y, cameraPos.z) > MAX_RENDER_DISTANCE_SQ) {
                    return@forEachIndexed
                }

                val bob = sin((world.time + entity.id) * 0.18).toDouble() * 0.045
                val offset = crewOffset(index, worksiteEntries.size)
                renderIcons(
                    client = client,
                    matrices = matrices,
                    vertexConsumers = vertexConsumers,
                    snapshot = entry.snapshot,
                    x = entity.x + offset.first,
                    y = entity.boundingBox.maxY + 0.65 + bob,
                    z = entity.z + offset.second,
                    seed = entity.id
                )
            }
        }

        vertexConsumers.draw()
        matrices.pop()

        RenderSystem.enableCull()
        RenderSystem.disableBlend()
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
            drawIconBacking(client, matrices, snapshot, x, y, z)
            renderFloatingStack(client, matrices, vertexConsumers, tagStack, x, y, z, 0.45f, seed)
        }

        if (snapshot.shouldShowStatusLabel()) {
            drawLabel(client, matrices, vertexConsumers, snapshot.statusLabel(), x, y + 0.18, z, labelColor(snapshot))
        }

        if (!snapshot.hasCargo()) return

        resolveCargoStack(snapshot.primaryCarriedItemId!!, snapshot.carriedItemCount)?.let { cargoStack ->
            renderFloatingStack(client, matrices, vertexConsumers, cargoStack, x, y - 0.28, z, 0.34f, seed * 31)
            if (snapshot.carriedItemCount > 1) {
                drawCount(client, matrices, vertexConsumers, snapshot.carriedItemCount.toString(), x, y - 0.51, z)
            }
        }
    }

    private fun drawIconBacking(
        client: MinecraftClient,
        matrices: MatrixStack,
        snapshot: CobblePalsNetworking.WorkerVisualSnapshot,
        x: Double,
        y: Double,
        z: Double
    ) {
        val color = haloColor(snapshot)
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        val alpha = if (snapshot.hasCargo()) 166 else 138
        val size = 0.44f
        val inset = 0.35f
        matrices.push()
        matrices.translate(x, y, z)
        matrices.multiply(client.entityRenderDispatcher.rotation)
        val matrix = matrices.peek().positionMatrix
        val buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        buffer.vertex(matrix, -size, -size, 0.08f).color(10, 16, 21, 210)
        buffer.vertex(matrix, -size, size, 0.08f).color(10, 16, 21, 210)
        buffer.vertex(matrix, size, size, 0.08f).color(10, 16, 21, 210)
        buffer.vertex(matrix, size, -size, 0.08f).color(10, 16, 21, 210)
        buffer.vertex(matrix, -inset, inset, 0.09f).color(r, g, b, alpha)
        buffer.vertex(matrix, inset, inset, 0.09f).color(r, g, b, alpha)
        buffer.vertex(matrix, inset, -inset, 0.09f).color(r, g, b, alpha)
        buffer.vertex(matrix, -inset, -inset, 0.09f).color(r, g, b, alpha)
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        RenderSystem.depthMask(false)
        BufferRenderer.drawWithGlobalProgram(buffer.end())
        RenderSystem.depthMask(true)
        matrices.pop()
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

    private fun drawLabel(
        client: MinecraftClient,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider.Immediate,
        label: String,
        x: Double,
        y: Double,
        z: Double,
        color: Int
    ) {
        val halfWidth = client.textRenderer.getWidth(label) / 2f
        matrices.push()
        matrices.translate(x, y, z)
        matrices.multiply(client.entityRenderDispatcher.rotation)
        matrices.scale(0.018f, -0.018f, 0.018f)
        client.textRenderer.draw(
            label,
            -halfWidth,
            0f,
            color,
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

    private fun CobblePalsNetworking.WorkerVisualSnapshot.statusReason(): WorkerStatusReason {
        return WorkerStatusReason.entries.getOrElse(statusReasonOrdinal) { WorkerStatusReason.READY }
    }

    private fun CobblePalsNetworking.WorkerVisualSnapshot.statusKind(): WorkerStatusKind {
        return statusReason().kind
    }

    private fun CobblePalsNetworking.WorkerVisualSnapshot.hasCargo(): Boolean {
        return !primaryCarriedItemId.isNullOrBlank() && carriedItemCount > 0
    }

    private fun CobblePalsNetworking.WorkerVisualSnapshot.shouldShowStatusLabel(): Boolean {
        return statusKind() == WorkerStatusKind.BLOCKED || statusKind() == WorkerStatusKind.STANDBY || statusReason() == WorkerStatusReason.PATH_BUDGET
    }

    private fun CobblePalsNetworking.WorkerVisualSnapshot.statusLabel(): String {
        return statusReason().label
    }

    private fun haloColor(snapshot: CobblePalsNetworking.WorkerVisualSnapshot): Int {
        return when (snapshot.statusKind()) {
            WorkerStatusKind.BLOCKED -> 0xE57373.toInt()
            WorkerStatusKind.STANDBY -> 0xB794F4.toInt()
            WorkerStatusKind.WAITING -> 0xD9A84F
            else -> if (snapshot.hasCargo()) phaseColor(snapshot.phase(), true) else familyColor(snapshot)
        }
    }

    private fun familyColor(snapshot: CobblePalsNetworking.WorkerVisualSnapshot): Int {
        return when (TagType.fromId(snapshot.tagTypeId)) {
            TagType.BREAKER, TagType.HARVESTER, TagType.VACUUM -> 0x56CF7A
            TagType.SENDER, TagType.PULLER, TagType.DISTRIBUTOR, TagType.DROPPER, TagType.VOID -> 0x33C7C4
            TagType.GUARDIAN -> 0xE57373.toInt()
            TagType.ACTIVATOR -> 0xF3C969
            TagType.SHEPHERD -> 0xF5A6C8.toInt()
            null -> phaseColor(snapshot.phase(), snapshot.hasCargo())
        }
    }

    private fun labelColor(snapshot: CobblePalsNetworking.WorkerVisualSnapshot): Int {
        return when (snapshot.statusKind()) {
            WorkerStatusKind.BLOCKED -> 0xFCA5A5.toInt()
            WorkerStatusKind.STANDBY -> 0xD6BCFA.toInt()
            WorkerStatusKind.WAITING -> 0xF6E05E.toInt()
            else -> 0xF6F3E8.toInt()
        }
    }

    private fun syncWorld(currentWorldKey: String) {
        if (worldKey == currentWorldKey) return
        worldKey = currentWorldKey
        overlays.clear()
    }

    private fun prune(now: Long) {
        overlays.entries.removeIf { (_, entry) -> now - entry.updatedAt > ENTRY_TTL_TICKS }
    }

    private fun crewOffset(index: Int, count: Int): Pair<Double, Double> {
        if (count <= 4) return 0.0 to 0.0
        val angle = (PI * 2.0 * index) / count.coerceAtLeast(1)
        val radius = if (count > 10) 0.36 else 0.22
        return cos(angle) * radius to sin(angle) * radius
    }

    private data class OverlayEntry(
        val worksitePos: BlockPos,
        val snapshot: CobblePalsNetworking.WorkerVisualSnapshot,
        val updatedAt: Long
    )
}