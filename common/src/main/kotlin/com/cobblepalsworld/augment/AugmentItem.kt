package com.cobblepalsworld.augment

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class AugmentItem(val augmentType: AugmentType, settings: Settings) : Item(settings) {
    override fun appendTooltip(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Text>,
        type: TooltipType
    ) {
        tooltip.add(Text.literal(augmentType.description).formatted(Formatting.GRAY))
        if (augmentType.stackable && augmentType.maxLevel > 1) {
            tooltip.add(Text.literal("Stackable (max ${augmentType.maxLevel})").formatted(Formatting.DARK_GRAY))
        }
    }
}
