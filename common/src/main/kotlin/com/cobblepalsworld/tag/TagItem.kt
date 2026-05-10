package com.cobblepalsworld.tag

import com.cobblepalsworld.tag.filter.FilterSerializer
import com.cobblepalsworld.tag.filter.TagFilter
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.navigation.ContainerFinder
import net.minecraft.entity.Entity
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

class TagItem(val tagType: TagType, settings: Settings, val isCanonicalItem: Boolean = true) : Item(settings) {

    companion object {
        private const val KEY_FILTER = "TagFilter"
        private const val KEY_BOUND_X = "BoundX"
        private const val KEY_BOUND_Y = "BoundY"
        private const val KEY_BOUND_Z = "BoundZ"
        private const val KEY_AREA_MIN_X = "AreaMinX"
        private const val KEY_AREA_MIN_Y = "AreaMinY"
        private const val KEY_AREA_MIN_Z = "AreaMinZ"
        private const val KEY_AREA_MAX_X = "AreaMaxX"
        private const val KEY_AREA_MAX_Y = "AreaMaxY"
        private const val KEY_AREA_MAX_Z = "AreaMaxZ"
        private const val KEY_PENDING_AREA_X = "PendingAreaX"
        private const val KEY_PENDING_AREA_Y = "PendingAreaY"
        private const val KEY_PENDING_AREA_Z = "PendingAreaZ"
        private const val KEY_SETTINGS = "TagSettings"

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

        fun getBoundArea(stack: ItemStack): BoundArea? {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return null
            if (!nbt.contains(KEY_AREA_MIN_X) || !nbt.contains(KEY_AREA_MAX_X)) return null
            return BoundArea(
                BlockPos(nbt.getInt(KEY_AREA_MIN_X), nbt.getInt(KEY_AREA_MIN_Y), nbt.getInt(KEY_AREA_MIN_Z)),
                BlockPos(nbt.getInt(KEY_AREA_MAX_X), nbt.getInt(KEY_AREA_MAX_Y), nbt.getInt(KEY_AREA_MAX_Z))
            )
        }

        fun setBoundArea(stack: ItemStack, area: BoundArea) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.putInt(KEY_AREA_MIN_X, area.min.x)
            nbt.putInt(KEY_AREA_MIN_Y, area.min.y)
            nbt.putInt(KEY_AREA_MIN_Z, area.min.z)
            nbt.putInt(KEY_AREA_MAX_X, area.max.x)
            nbt.putInt(KEY_AREA_MAX_Y, area.max.y)
            nbt.putInt(KEY_AREA_MAX_Z, area.max.z)
            nbt.remove(KEY_BOUND_X)
            nbt.remove(KEY_BOUND_Y)
            nbt.remove(KEY_BOUND_Z)
            nbt.remove(KEY_PENDING_AREA_X)
            nbt.remove(KEY_PENDING_AREA_Y)
            nbt.remove(KEY_PENDING_AREA_Z)
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
        }

        fun getPendingAreaStart(stack: ItemStack): BlockPos? {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return null
            if (!nbt.contains(KEY_PENDING_AREA_X)) return null
            return BlockPos(nbt.getInt(KEY_PENDING_AREA_X), nbt.getInt(KEY_PENDING_AREA_Y), nbt.getInt(KEY_PENDING_AREA_Z))
        }

        private fun setPendingAreaStart(stack: ItemStack, pos: BlockPos) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.putInt(KEY_PENDING_AREA_X, pos.x)
            nbt.putInt(KEY_PENDING_AREA_Y, pos.y)
            nbt.putInt(KEY_PENDING_AREA_Z, pos.z)
            nbt.remove(KEY_AREA_MIN_X)
            nbt.remove(KEY_AREA_MIN_Y)
            nbt.remove(KEY_AREA_MIN_Z)
            nbt.remove(KEY_AREA_MAX_X)
            nbt.remove(KEY_AREA_MAX_Y)
            nbt.remove(KEY_AREA_MAX_Z)
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
        }

        private fun clearPendingAreaStart(stack: ItemStack) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return
            nbt.remove(KEY_PENDING_AREA_X)
            nbt.remove(KEY_PENDING_AREA_Y)
            nbt.remove(KEY_PENDING_AREA_Z)
            if (nbt.isEmpty) {
                stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
            } else {
                stack.set(
                    net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                    net.minecraft.component.type.NbtComponent.of(nbt)
                )
            }
        }

        fun clearBinding(stack: ItemStack) {
            clearBoundPos(stack)
            clearPendingAreaStart(stack)
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return
            nbt.remove(KEY_AREA_MIN_X)
            nbt.remove(KEY_AREA_MIN_Y)
            nbt.remove(KEY_AREA_MIN_Z)
            nbt.remove(KEY_AREA_MAX_X)
            nbt.remove(KEY_AREA_MAX_Y)
            nbt.remove(KEY_AREA_MAX_Z)
            if (nbt.isEmpty) {
                stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
            } else {
                stack.set(
                    net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                    net.minecraft.component.type.NbtComponent.of(nbt)
                )
            }
        }

        fun getSettings(stack: ItemStack): TagSettings {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return TagSettings.EMPTY
            if (!nbt.contains(KEY_SETTINGS)) return TagSettings.EMPTY
            return TagSettingsSerializer.fromNbt(nbt.getCompound(KEY_SETTINGS))
        }

        fun setSettings(stack: ItemStack, settings: TagSettings) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.put(KEY_SETTINGS, TagSettingsSerializer.toNbt(settings))
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
        }

    }

    override fun inventoryTick(stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean) {
        super.inventoryTick(stack, world, entity, slot, selected)
        if (world.isClient || isCanonicalItem) return

        val player = entity as? PlayerEntity ?: return
        val normalized = TagRegistry.normalizeStack(stack)
        if (normalized.item !== stack.item) {
            player.inventory.setStack(slot, normalized)
        }
    }

    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (!player.isSneaking) return ActionResult.PASS
        if (!tagType.supportsBinding) return ActionResult.PASS

        val world = context.world
        val pos = context.blockPos

        if (!world.isClient) {
            val stack = normalizedHeldStack(player, context.hand)
            val settings = getSettings(stack)
            val dimensionId = world.registryKey.value.toString()
            when (tagType.bindingMode) {
                BindingMode.NONE -> return ActionResult.PASS
                BindingMode.AREA -> {
                    val start = getPendingAreaStart(stack)
                    if (start == null) {
                        setPendingAreaStart(stack, pos)
                        player.sendMessage(
                            Text.literal("First corner set at ${pos.x}, ${pos.y}, ${pos.z}. Sneak-use a second corner.").formatted(Formatting.YELLOW),
                            true
                        )
                    } else {
                        val area = BoundArea.of(start, pos)
                        setBoundArea(stack, area)
                        player.sendMessage(
                            Text.literal("Bound area: ${area.width()}x${area.height()}x${area.depth()}").formatted(Formatting.GREEN),
                            true
                        )
                    }
                }
                BindingMode.POSITION -> {
                    clearPendingAreaStart(stack)
                    setBoundPos(stack, pos)
                    if (tagType.supportsTargetList) {
                        val filteredTargets = settings.extraTargets.filterNot { it.dimensionId == dimensionId && it.pos == pos }
                        setSettings(stack, settings.copy(extraTargets = filteredTargets))
                    }
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

                    val currentBound = getBoundPos(stack)
                    if (tagType.supportsTargetList && currentBound != null && currentBound != pos) {
                        val existingIndex = settings.extraTargets.indexOfFirst { it.dimensionId == dimensionId && it.pos == pos }
                        val updatedTargets = settings.extraTargets.toMutableList()
                        if (existingIndex >= 0) {
                            updatedTargets.removeAt(existingIndex)
                            setSettings(stack, settings.copy(extraTargets = updatedTargets))
                            player.sendMessage(
                                Text.literal("Removed extra target at ${pos.x}, ${pos.y}, ${pos.z}").formatted(Formatting.YELLOW),
                                true
                            )
                        } else {
                            updatedTargets += TagTarget(dimensionId, pos.toImmutable())
                            setSettings(stack, settings.copy(extraTargets = updatedTargets))
                            player.sendMessage(
                                Text.literal("Added extra target at ${pos.x}, ${pos.y}, ${pos.z}").formatted(Formatting.GREEN),
                                true
                            )
                        }
                        return ActionResult.SUCCESS
                    }

                    clearPendingAreaStart(stack)
                    setBoundPos(stack, pos)
                    if (tagType.supportsTargetList) {
                        val filteredTargets = settings.extraTargets.filterNot { it.dimensionId == dimensionId && it.pos == pos }
                        setSettings(stack, settings.copy(extraTargets = filteredTargets))
                    }
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

        val stack = if (!world.isClient) normalizedHeldStack(user, hand) else user.getStackInHand(hand)

        if (user.isSneaking) {
            if (tagType.supportsBinding) {
                if (!world.isClient) {
                    if (getBoundPos(stack) != null || getBoundArea(stack) != null || getPendingAreaStart(stack) != null) {
                        clearBinding(stack)
                        if (tagType.supportsTargetList) {
                            setSettings(stack, getSettings(stack).copy(extraTargets = emptyList()))
                        }
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
        return TypedActionResult.success(stack, world.isClient)
    }

    override fun appendTooltip(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Text>,
        type: TooltipType
    ) {
        tooltip.add(Text.literal(tagType.description).formatted(Formatting.BLUE))

        val registries = context.registryLookup ?: return
        if (tagType.usesFilter) {
            val filter = getFilter(stack, registries)
            val mode = if (filter.whitelist) "Whitelist" else "Blacklist"
            tooltip.add(Text.literal("Mode: $mode").formatted(Formatting.GRAY))
            tooltip.add(Text.literal("Match: ${filter.matchMode.name.lowercase().replaceFirstChar(Char::titlecase)}").formatted(Formatting.DARK_GRAY))
            if (filter.items.isNotEmpty()) {
                tooltip.add(Text.literal("Filter: ${filter.items.size} item(s)").formatted(Formatting.DARK_GRAY))
            }
            if (filter.whitelist && filter.items.isEmpty()) {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.whitelist_empty").formatted(Formatting.RED))
            }
        }

        val settings = getSettings(stack)
        if (settings.redstoneMode != RedstoneControlMode.ALWAYS) {
            tooltip.add(Text.literal("Signal: ${humanValue(settings.redstoneMode.id)}").formatted(Formatting.GOLD))
        }
        if (tagType.supportsTargetList) {
            tooltip.add(Text.literal("Target: ${humanValue(settings.targetStrategy.id)}").formatted(Formatting.AQUA))
            tooltip.add(Text.literal("Run: ${if (settings.terminateAfterSuccess) "One pass" else "Loop"}").formatted(Formatting.DARK_AQUA))
            if (settings.regulatorAmount != 64) {
                tooltip.add(Text.literal("Regulator: ${settings.regulatorAmount}").formatted(Formatting.GREEN))
            }
        }

        if (tagType.supportsBinding) {
            val bound = getBoundPos(stack)
            val area = getBoundArea(stack)
            val pending = getPendingAreaStart(stack)
            if (area != null) {
                tooltip.add(Text.literal("Bound Box: ${area.min.x}, ${area.min.y}, ${area.min.z}").formatted(Formatting.GREEN))
                tooltip.add(Text.literal("to ${area.max.x}, ${area.max.y}, ${area.max.z} (${area.width()}x${area.height()}x${area.depth()})").formatted(Formatting.GREEN))
            } else if (bound != null) {
                val label = when (tagType.bindingMode) {
                    BindingMode.CONTAINER -> "Bound Container"
                    BindingMode.POSITION -> when (tagType) {
                        TagType.BREAKER -> "Bound Block"
                        TagType.ACTIVATOR -> "Bound Target"
                        else -> "Bound Position"
                    }
                    BindingMode.AREA -> "Bound Box"
                    BindingMode.NONE -> "Bound"
                }
                tooltip.add(Text.literal("$label: ${bound.x}, ${bound.y}, ${bound.z}").formatted(Formatting.GREEN))
            } else if (pending != null) {
                tooltip.add(Text.literal("Area Start: ${pending.x}, ${pending.y}, ${pending.z}").formatted(Formatting.YELLOW))
            } else {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.bind_hint").formatted(Formatting.YELLOW))
            }
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.clear_hint").formatted(Formatting.DARK_GRAY))
        }

        if (tagType.supportsTargetList && settings.extraTargets.isNotEmpty()) {
            tooltip.add(Text.literal("Extra Targets: ${settings.extraTargets.size}").formatted(Formatting.AQUA))
        }
    }

    private fun humanValue(value: String): String =
        value.split('_').joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) }

    private fun normalizedHeldStack(user: PlayerEntity, hand: Hand): ItemStack {
        val current = user.getStackInHand(hand)
        val normalized = TagRegistry.normalizeStack(current)
        if (normalized.item !== current.item) {
            user.setStackInHand(hand, normalized)
        }
        return normalized
    }
}
