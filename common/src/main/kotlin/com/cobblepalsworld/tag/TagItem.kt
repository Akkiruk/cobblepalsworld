package com.cobblepalsworld.tag

import com.cobblepalsworld.augment.AugmentSet
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
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryWrapper
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

class TagItem(val tagType: TagType, settings: Settings) : Item(settings) {

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
        /** Monotonic edit counter, bumped on every data write (used for client cache invalidation). */
        private const val KEY_REVISION = "TagRevision"
        /** Structural format version of the stack's tag data. Bump alongside [TAG_DATA_VERSION] migrations. */
        private const val KEY_DATA_VERSION = "TagDataVersion"
        private const val KEY_TRACKING_ID = "TagTrackingId"

        /** Current structural version of the tag data written to item stacks. */
        const val TAG_DATA_VERSION = 1

        /** How often (in ticks) a held bound tag re-emits its binding preview particles. */
        private const val PREVIEW_INTERVAL_TICKS = 10L

        /** Maximum distance at which binding preview particles are shown to the holder. */
        private const val PREVIEW_MAX_DISTANCE = 64.0

        fun getRevision(stack: ItemStack): Long {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return 0L
            return if (nbt.contains(KEY_REVISION)) nbt.getLong(KEY_REVISION) else 0L
        }

        fun getTrackingId(stack: ItemStack): String? {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: return null
            return if (nbt.contains(KEY_TRACKING_ID)) nbt.getString(KEY_TRACKING_ID) else null
        }

        fun ensureTrackingId(stack: ItemStack): String {
            getTrackingId(stack)?.let { return it }

            val trackingId = UUID.randomUUID().toString()
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.putString(KEY_TRACKING_ID, trackingId)
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
            return trackingId
        }

        fun getSpec(stack: ItemStack, registries: RegistryWrapper.WrapperLookup): TagSpec? {
            val tagItem = stack.item as? TagItem ?: return null
            upgradeStackData(stack)
            return TagSpec(
                type = tagItem.tagType,
                filter = getFilter(stack, registries),
                boundPos = getBoundPos(stack),
                boundArea = getBoundArea(stack),
                settings = getSettings(stack)
            )
        }

        fun setSpec(stack: ItemStack, spec: TagSpec, registries: RegistryWrapper.WrapperLookup) {
            setFilter(stack, spec.filter, registries)
            clearBinding(stack)
            spec.boundArea?.let { setBoundArea(stack, it) } ?: spec.boundPos?.let { setBoundPos(stack, it) }
            setSettings(stack, spec.settings)
        }

        fun toTagInstance(
            stack: ItemStack,
            registries: RegistryWrapper.WrapperLookup,
            augments: AugmentSet = AugmentSet.EMPTY,
            controllerPos: BlockPos? = null
        ): TagInstance? {
            val spec = getSpec(stack, registries) ?: return null
            return spec.toTagInstance(augments = augments, controllerPos = controllerPos)
        }

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
            bumpRevision(stack)
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
            bumpRevision(stack)
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
            bumpRevision(stack)
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
            bumpRevision(stack)
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
            bumpRevision(stack)
        }

        private fun bumpRevision(stack: ItemStack) {
            val nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                ?.copyNbt() ?: NbtCompound()
            nbt.putLong(KEY_REVISION, nbt.getLong(KEY_REVISION) + 1L)
            nbt.putInt(KEY_DATA_VERSION, TAG_DATA_VERSION)
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
        }

        /**
         * Migrates older tag-stack data layouts to [TAG_DATA_VERSION] in place.
         * Version 0 (pre-versioning) stacks are structurally identical to v1,
         * so today this only stamps the version; future layout changes add
         * sequential upgrade steps here, mirroring SaveMigrations.
         */
        private fun upgradeStackData(stack: ItemStack) {
            val component = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA) ?: return
            val nbt = component.copyNbt()
            val version = if (nbt.contains(KEY_DATA_VERSION)) nbt.getInt(KEY_DATA_VERSION) else 0
            if (version >= TAG_DATA_VERSION) return
            nbt.putInt(KEY_DATA_VERSION, TAG_DATA_VERSION)
            stack.set(
                net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(nbt)
            )
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
                        playBindingSound(world, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f)
                        markBindingTarget(world, player, pos)
                        player.sendMessage(
                            Text.translatable("message.cobblepalsworld.area_first_corner", pos.x, pos.y, pos.z).formatted(Formatting.YELLOW),
                            true
                        )
                    } else {
                        val area = BoundArea.of(start, pos)
                        setBoundArea(stack, area)
                        playBindingSound(world, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 1.0f)
                        markBindingTarget(world, player, pos)
                        player.sendMessage(
                            Text.translatable("message.cobblepalsworld.area_bound", area.width(), area.height(), area.depth()).formatted(Formatting.GREEN),
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
                    playBindingSound(world, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 1.0f)
                    markBindingTarget(world, player, pos)
                    player.sendMessage(
                        Text.translatable("message.cobblepalsworld.bound_position", pos.x, pos.y, pos.z).formatted(Formatting.GREEN),
                        true
                    )
                }
                BindingMode.CONTAINER -> {
                    if (!ContainerFinder.isContainer(world, pos)) {
                        playBindingSound(world, pos, SoundEvents.ENTITY_VILLAGER_NO, 1.0f)
                        player.sendMessage(
                            Text.translatable("message.cobblepalsworld.must_bind_container").formatted(Formatting.RED),
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
                            playBindingSound(world, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f)
                            player.sendMessage(
                                Text.translatable("message.cobblepalsworld.extra_target_removed", pos.x, pos.y, pos.z).formatted(Formatting.YELLOW),
                                true
                            )
                        } else {
                            updatedTargets += TagTarget(dimensionId, pos.toImmutable())
                            setSettings(stack, settings.copy(extraTargets = updatedTargets))
                            playBindingSound(world, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f)
                            markBindingTarget(world, player, pos)
                            player.sendMessage(
                                Text.translatable("message.cobblepalsworld.extra_target_added", pos.x, pos.y, pos.z).formatted(Formatting.GREEN),
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
                    playBindingSound(world, pos, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 1.0f)
                    markBindingTarget(world, player, pos)
                    player.sendMessage(
                        Text.translatable("message.cobblepalsworld.bound_container", pos.x, pos.y, pos.z).formatted(Formatting.GREEN),
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
                        playBindingSound(world, user.blockPos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, 0.9f)
                        user.sendMessage(
                            Text.translatable("message.cobblepalsworld.binding_cleared").formatted(Formatting.YELLOW), true
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
        tooltip.add(Text.translatable("tooltip.cobblepalsworld.tag.${tagType.id}.desc").formatted(Formatting.BLUE))

        val registries = context.registryLookup ?: return
        if (tagType.usesFilter) {
            val filter = getFilter(stack, registries)
            val mode = Text.translatable(if (filter.whitelist) "tooltip.cobblepalsworld.mode.whitelist" else "tooltip.cobblepalsworld.mode.blacklist")
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.mode", mode).formatted(Formatting.GRAY))
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.match", filter.matchMode.name.lowercase().replaceFirstChar(Char::titlecase)).formatted(Formatting.DARK_GRAY))
            val filterParts = mutableListOf<String>()
            if (filter.items.isNotEmpty()) {
                filterParts += Text.translatable("tooltip.cobblepalsworld.filter_count.items", filter.items.size).string
            }
            if (filter.matchTags.isNotEmpty()) {
                filterParts += Text.translatable("tooltip.cobblepalsworld.filter_count.tags", filter.matchTags.size).string
            }
            if (filter.matchModIds.isNotEmpty()) {
                filterParts += Text.translatable("tooltip.cobblepalsworld.filter_count.mods", filter.matchModIds.size).string
            }
            if (filterParts.isNotEmpty()) {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.filter_summary", filterParts.joinToString(", ")).formatted(Formatting.DARK_GRAY))
                val behaviorKey = when (filter.matchMode) {
                    com.cobblepalsworld.tag.filter.FilterMatchMode.ANY -> "tooltip.cobblepalsworld.match_behavior.any"
                    com.cobblepalsworld.tag.filter.FilterMatchMode.ALL -> "tooltip.cobblepalsworld.match_behavior.all"
                }
                tooltip.add(Text.translatable(behaviorKey).formatted(Formatting.DARK_AQUA))
            }
            if (filter.whitelist && filter.isEmpty()) {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.whitelist_empty").formatted(Formatting.RED))
            }
        }

        val settings = getSettings(stack)
        if (settings.redstoneMode != RedstoneControlMode.ALWAYS) {
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.signal", humanValue(settings.redstoneMode.id)).formatted(Formatting.GOLD))
        }
        if (tagType.supportsTargetList) {
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.target", humanValue(settings.targetStrategy.id)).formatted(Formatting.AQUA))
            val runMode = Text.translatable(if (settings.terminateAfterSuccess) "tooltip.cobblepalsworld.run.one_pass" else "tooltip.cobblepalsworld.run.loop")
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.run", runMode).formatted(Formatting.DARK_AQUA))
            if (settings.regulatorAmount != 64) {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.regulator", settings.regulatorAmount).formatted(Formatting.GREEN))
            }
        }

        if (tagType.supportsBinding) {
            val bound = getBoundPos(stack)
            val area = getBoundArea(stack)
            val pending = getPendingAreaStart(stack)
            if (area != null) {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.bound_box", area.min.x, area.min.y, area.min.z).formatted(Formatting.GREEN))
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.bound_box_to", area.max.x, area.max.y, area.max.z, area.width(), area.height(), area.depth()).formatted(Formatting.GREEN))
            } else if (bound != null) {
                val labelKey = when (tagType.bindingMode) {
                    BindingMode.CONTAINER -> "tooltip.cobblepalsworld.bound_label.container"
                    BindingMode.POSITION -> when (tagType) {
                        TagType.BREAKER -> "tooltip.cobblepalsworld.bound_label.block"
                        TagType.ACTIVATOR -> "tooltip.cobblepalsworld.bound_label.target"
                        else -> "tooltip.cobblepalsworld.bound_label.position"
                    }
                    BindingMode.AREA -> "tooltip.cobblepalsworld.bound_label.box"
                    BindingMode.NONE -> "tooltip.cobblepalsworld.bound_label.generic"
                }
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.bound_at", Text.translatable(labelKey), bound.x, bound.y, bound.z).formatted(Formatting.GREEN))
            } else if (pending != null) {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.area_start", pending.x, pending.y, pending.z).formatted(Formatting.YELLOW))
            } else {
                tooltip.add(Text.translatable("tooltip.cobblepalsworld.bind_hint").formatted(Formatting.YELLOW))
            }
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.clear_hint").formatted(Formatting.DARK_GRAY))
        }

        if (tagType.supportsTargetList && settings.extraTargets.isNotEmpty()) {
            tooltip.add(Text.translatable("tooltip.cobblepalsworld.extra_targets", settings.extraTargets.size).formatted(Formatting.AQUA))
        }

        tooltip.add(Text.translatable("tooltip.cobblepalsworld.open_editor_hint").formatted(Formatting.GRAY))
        tooltip.add(Text.translatable("tooltip.cobblepalsworld.edit_hint").formatted(Formatting.DARK_GRAY))
    }

    /**
     * While a bound tag is held, gently highlight its bound target(s) in-world so
     * players can see at a glance where the tag points without reading coordinates.
     */
    override fun inventoryTick(stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean) {
        if (world.isClient || world !is ServerWorld) return
        if (world.time % PREVIEW_INTERVAL_TICKS != 0L) return
        val player = entity as? ServerPlayerEntity ?: return
        if (!selected && player.offHandStack !== stack) return
        if (!tagType.supportsBinding) return

        val area = getBoundArea(stack)
        if (area != null) {
            for (corner in area.corners()) {
                if (corner.isWithinDistance(player.pos, PREVIEW_MAX_DISTANCE)) {
                    spawnPreviewParticle(world, player, corner, ParticleTypes.END_ROD)
                }
            }
            return
        }

        getBoundPos(stack)?.let { bound ->
            if (bound.isWithinDistance(player.pos, PREVIEW_MAX_DISTANCE)) {
                spawnPreviewParticle(world, player, bound, ParticleTypes.END_ROD)
            }
            val dimensionId = world.registryKey.value.toString()
            getSettings(stack).extraTargets.forEach { target ->
                if (target.dimensionId == dimensionId && target.pos.isWithinDistance(player.pos, PREVIEW_MAX_DISTANCE)) {
                    spawnPreviewParticle(world, player, target.pos, ParticleTypes.END_ROD)
                }
            }
            return
        }

        getPendingAreaStart(stack)?.let { pending ->
            if (pending.isWithinDistance(player.pos, PREVIEW_MAX_DISTANCE)) {
                spawnPreviewParticle(world, player, pending, ParticleTypes.FLAME)
            }
        }
    }

    private fun spawnPreviewParticle(world: ServerWorld, player: ServerPlayerEntity, pos: BlockPos, particle: net.minecraft.particle.ParticleEffect) {
        world.spawnParticles(
            player,
            particle,
            true,
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
            2,
            0.25, 0.25, 0.25,
            0.0
        )
    }

    private fun playBindingSound(world: World, pos: BlockPos, sound: SoundEvent, pitch: Float) {
        world.playSound(null, pos, sound, SoundCategory.PLAYERS, 0.6f, pitch)
    }

    private fun markBindingTarget(world: World, player: PlayerEntity, pos: BlockPos) {
        val sw = world as? ServerWorld ?: return
        val viewer = player as? ServerPlayerEntity ?: return
        sw.spawnParticles(
            viewer,
            ParticleTypes.HAPPY_VILLAGER,
            true,
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
            6,
            0.35, 0.35, 0.35,
            0.0
        )
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
