package com.cobblepalsworld.fabric

import com.cobblepalsworld.CobblePalsWorldClient
import com.cobblepalsworld.visual.TagHighlightRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents

object CobblePalsWorldFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblePalsWorldClient.init()

        WorldRenderEvents.LAST.register { context ->
            val matrices = context.matrixStack() ?: return@register
            TagHighlightRenderer.render(matrices)
        }
    }
}
