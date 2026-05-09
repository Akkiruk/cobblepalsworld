package com.cobblepalsworld.tag

import com.cobblepalsworld.tag.filter.FilterSerializer
import com.cobblepalsworld.tag.filter.TagFilter
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.navigation.ContainerFinder
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class TagItem(val tagType: TagType, settings: Settings) : Item(settings) {

    companion object {
        private const val KEY_FILTER = "TagFilter"
        private const val KEY_BOUND_X = "BoundX"
        private const val KEY_BOUND_Y = "BoundY"
        private const val KEY_BOUND_Z = "BoundZ"

        fun getFilter(stack: ItemStack, registries: RegistryWrapper.WrapperLookup): TagFilter {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return TagFilter.EMPTY
            if (!nbt.contains(KEY_FILTER)) return TagFilter.EMPTY
            return FilterSerializer.fromNbt(nbt.getCompound(KEY_FILTER), registries)
        }

        fun setFilter(stack: ItemStack, filter: TagFilter, registries: RegistryWrapper.WrapperLookup) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.put(KEY_FILTER, FilterSerializer.toNbt(filter, registries))
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
        }

        fun getBoundPos(stack: ItemStack): BlockPos? {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return null
            if (!nbt.contains(KEY_BOUND_X)) return null
            return BlockPos(nbt.getInt(KEY_BOUND_X), nbt.getInt(KEY_BOUND_Y), nbt.getInt(KEY_BOUND_Z))
        }

        fun setBoundPos(stack: ItemStack, pos: BlockPos) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.putInt(KEY_BOUND_X, pos.x)
            nbt.putInt(KEY_BOUND_Y, pos.y)
            nbt.putInt(KEY_BOUND_Z, pos.z)
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
        }

        fun clearBoundPos(stack: ItemStack) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return
            nbt.remove(KEY_BOUND_X)
            nbt.remove(KEY_BOUND_Y)
            nbt.remove(KEY_BOUND_Z)
            if (nbt.isEmpty) {
                stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
            } else {
                stack.set(
                    net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                    net.minecraft.component.type.NbtComponent.of(nbt)
                )
            }
        }

    }

    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (!player.isSneaking) return ActionResult.PASS
        if (!tagType.supportsBinding) return ActionResult.PASS

        val world = context.world
        val pos = context.blockPos

        if (!world.isClient) {
            val stack = player.getStackInHand(context.hand)
            when (tagType.bindingMode) {
                BindingMode.NONE -> return ActionResult.PASS
                BindingMode.POSITION -> {
                    setBoundPos(stack, pos)
                    player.sendMessage(
                        Text.literal("Bound to ${pos.x}, ${pos.y}, ${pos.z}").formatted(Formatting.GREEN),
                        true
                    )
                }
                BindingMode.CONTAINER -> {
                    if (!ContainerFinder.isContainer(world, pos)) {
                        player.sendMessage(
                            Text.literal("This tag must bind to a container!").formatted(Formatting.RED),
                            true
                        )
                        return ActionResult.FAIL
                    }

                    setBoundPos(stack, pos)
                    player.sendMessage(
                        Text.literal("Bound to container at ${pos.x}, ${pos.y}, ${pos.z}").formatted(Formatting.GREEN),
                        true
                    )
                }
            }
        }
        return ActionResult.SUCCESS
    }

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        // If a screen was JUST opened (e.g., entity interaction → tag assignment GUI),
        // the player's screen handler is no longer the default. Don't process use().
        if (user.currentScreenHandler !is net.minecraft.screen.PlayerScreenHandler) {
            return TypedActionResult.pass(user.getStackInHand(hand))
        }

        if (user.isSneaking) {
            if (tagType.supportsBinding) {
                if (!world.isClient) {
                    val stack = user.getStackInHand(hand)
                    if (getBoundPos(stack) != null) {
                        clearBoundPos(stack)
                        user.sendMessage(
                            Text.literal("Binding cleared").formatted(Formatting.YELLOW), true
                        )
                    }
                }
                return TypedActionResult.success(user.getStackInHand(hand), world.isClient)
            }
            return TypedActionResult.pass(user.getStackInHand(hand))
        }

        if (!world.isClient) {
            user.openHandledScreen(SimpleNamedScreenHandlerFactory(
                { syncId, inv, _ -> TagFilterScreenHandler(syncId, inv, hand) },
                Text.translatable("screen.cobblepalsworld.tag_filter")
            ))
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient)
    }

    override fun appendTooltip(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Text>,
        type: TooltipType
    ) {
        tooltip.add(Text.literal(tagType.description).formatted(Formatting.BLUE))

        val registries = context.registryLookup ?: return
        val filter = getFilter(stack, registries)
        val mode = if (filter.whitelist) "Whitelist" else "Blacklist"
        tooltip.add(Text.literal("Mode: $mode").formatted(Formatting.GRAY))
        if (filter.items.isNotEmpty()) {
            tooltip.add(Text.literal("Filter: ${filter.items.size} item(s)").formatted(Formatting.DARK_GRAY))
        }
        if (filter.whitelist && filter.items.isEmpty()) {
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.whitelist_empty").formatted(Formatting.RED))
        }

        if (tagType.supportsBinding) {
            val bound = getBoundPos(stack)
            if (bound != null) {
                val label = when (tagType.bindingMode) {
                    BindingMode.CONTAINER -> "Bound Container"
                    BindingMode.POSITION -> "Bound Area"
                    BindingMode.NONE -> "Bound"
                }
                tooltip.add(Text.literal("$label: ${bound.x}, ${bound.y}, ${bound.z}").formatted(Formatting.GREEN))
            } else {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.bind_hint").formatted(Formatting.YELLOW))
            }
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.clear_hint").formatted(Formatting.DARK_GRAY))
        }
    }
}
