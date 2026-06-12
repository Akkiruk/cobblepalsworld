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
        tooltip.add(Text.translatable("tooltip.cobblepalsworld.augment.${augmentType.id}.desc").formatted(Formatting.GRAY))
        if (augmentType.stackable && augmentType.maxLevel > 1) {
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.augment.stacks_to", augmentType.maxLevel).formatted(Formatting.DARK_GRAY))
        } else {
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.augment.single_slot").formatted(Formatting.DARK_GRAY))
        }
        tooltip.add(Text.translatable("tooltip.cobblepalsworld.augment.install_hint").formatted(Formatting.DARK_AQUA))
    }
}
