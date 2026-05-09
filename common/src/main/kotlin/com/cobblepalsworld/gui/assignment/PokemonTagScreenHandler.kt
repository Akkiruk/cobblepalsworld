package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import java.util.UUID

class PokemonTagScreenHandler : ScreenHandler {
    private val tagInventory: SimpleInventory
    private val augmentInventory: SimpleInventory
    private val pokemonDisplayInventory: SimpleInventory
    val pokemonId: UUID?
    private val invData: PropertyDelegate

    val carryCount: Int get() = invData.get(0)
    val carryMax: Int get() = invData.get(1)
    val workerPhase: Int get() = invData.get(2)
    val isEcoMode: Boolean get() = invData.get(3) != 0
    val isManagedByCommandPost: Boolean get() = invData.get(4) != 0

    companion object {
        const val AUGMENT_SLOT_COUNT = AugmentSet.MAX_AUGMENT_SLOTS // 3
        const val TAG_SLOT = 0
        const val AUGMENT_SLOT_START = 1
        const val AUGMENT_SLOT_END = AUGMENT_SLOT_START + AUGMENT_SLOT_COUNT // 4
        const val DISPLAY_SLOT_START = AUGMENT_SLOT_END // 4
        const val DISPLAY_SLOT_END = DISPLAY_SLOT_START + 9 // 13
        const val PLAYER_SLOT_START = DISPLAY_SLOT_END // 13
    }

    // Client constructor
    constructor(syncId: Int, playerInventory: PlayerInventory) : super(MenuTypes.POKEMON_TAG.get(), syncId) {
        this.tagInventory = SimpleInventory(1)
        this.augmentInventory = SimpleInventory(AUGMENT_SLOT_COUNT)
        this.pokemonDisplayInventory = SimpleInventory(9)
        this.pokemonId = null
        this.invData = ArrayPropertyDelegate(5)
        setupSlots(playerInventory)
    }

    // Server constructor
    constructor(syncId: Int, playerInventory: PlayerInventory, pokemonId: UUID) : super(MenuTypes.POKEMON_TAG.get(), syncId) {
        this.tagInventory = SimpleInventory(1)
        this.augmentInventory = SimpleInventory(AUGMENT_SLOT_COUNT)
        this.pokemonDisplayInventory = SimpleInventory(9)
        this.pokemonId = pokemonId
        this.invData = object : PropertyDelegate {
            override fun get(index: Int): Int {
                val pokemonInv = InventoryManager.get(pokemonId)
                val workerState = StateManager.get(pokemonId)
                val isManagedByCommandPost = TagAssignmentManager.getControllerBinding(pokemonId) != null
                return when (index) {
                    0 -> pokemonInv?.let { inv -> (0 until inv.size()).count { !inv.getStack(it).isEmpty } } ?: 0
                    1 -> pokemonInv?.size() ?: 0
                    2 -> workerState?.phase?.ordinal ?: 0
                    3 -> if (workerState?.ecoMode == true) 1 else 0
                    4 -> if (isManagedByCommandPost) 1 else 0
                    else -> 0
                }
            }
            override fun set(index: Int, value: Int) {}
            override fun size(): Int = 5
        }

        val pokemonInv = InventoryManager.get(pokemonId)
        if (pokemonInv != null) {
            for (i in 0 until minOf(pokemonInv.size(), 9)) {
                pokemonDisplayInventory.setStack(i, pokemonInv.getStack(i).copy())
            }
        }

        val existing = TagAssignmentManager.get(pokemonId)
        if (existing != null) {
            val item = TagRegistry.getItem(existing.type)
            if (item != null) {
                val stack = ItemStack(item)
                TagItem.setFilter(stack, existing.filter, playerInventory.player.world.registryManager)
                existing.boundPos?.let { TagItem.setBoundPos(stack, it) }
                TagItem.setSettings(stack, existing.settings)
                tagInventory.setStack(0, stack)
            }
            for ((i, entry) in collapseAugmentsForDisplay(existing.augments).withIndex()) {
                if (i >= AUGMENT_SLOT_COUNT) break
                val augItem = com.cobblepalsworld.augment.AugmentRegistry.getItem(entry.type) ?: continue
                augmentInventory.setStack(i, ItemStack(augItem, entry.level))
            }
        }
        setupSlots(playerInventory)
    }

    private fun setupSlots(playerInventory: PlayerInventory) {
        addProperties(invData)

        // Slot 0: Tag slot (gold panel, center-left)
        addSlot(TagSlot(tagInventory, 0, 32, 18))

        // Slots 1-3: Augment slots (left column)
        for (i in 0 until AUGMENT_SLOT_COUNT) {
            addSlot(AugmentSlot(augmentInventory, i, 8, 18 + i * 18))
        }

        // Slots 4-12: 3x3 pokémon inventory display (read-only)
        for (row in 0..2) {
            for (col in 0..2) {
                addSlot(LockedSlot(pokemonDisplayInventory, row * 3 + col, 60 + col * 18, 18 + row * 18))
            }
        }

        // Player inventory (3 rows)
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 83 + row * 18))
            }
        }
        // Hotbar
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 145))
        }
    }

    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack {
        if (slotIndex == TAG_SLOT) {
            val slot = slots[TAG_SLOT]
            if (!slot.hasStack()) return ItemStack.EMPTY
            val stack = slot.stack
            if (!insertItem(stack, PLAYER_SLOT_START, slots.size, true)) return ItemStack.EMPTY
            slot.onQuickTransfer(stack, slot.stack)
            if (stack.isEmpty) slot.stack = ItemStack.EMPTY else slot.markDirty()
            return stack.copy()
        }
        // Augment slots: shift-click returns to player
        if (slotIndex in AUGMENT_SLOT_START until AUGMENT_SLOT_END) {
            val slot = slots[slotIndex]
            if (!slot.hasStack()) return ItemStack.EMPTY
            val stack = slot.stack
            if (!insertItem(stack, PLAYER_SLOT_START, slots.size, true)) return ItemStack.EMPTY
            if (stack.isEmpty) slot.stack = ItemStack.EMPTY else slot.markDirty()
            return stack.copy()
        }
        // Display slots are locked
        if (slotIndex in DISPLAY_SLOT_START until DISPLAY_SLOT_END) return ItemStack.EMPTY
        // Player inventory shift-click
        val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (!slot.hasStack()) return ItemStack.EMPTY
        val stack = slot.stack
        if (stack.item is TagItem && slots[TAG_SLOT].stack.isEmpty) {
            slots[TAG_SLOT].stack = stack.split(1)
            slot.markDirty()
        } else if (stack.item is AugmentItem) {
            insertAugmentLevels(stack)
            slot.markDirty()
        }
        return ItemStack.EMPTY
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        val currentPokemonId = pokemonId
        if (!player.world.isClient && currentPokemonId != null) {
            if (TagAssignmentManager.getControllerBinding(currentPokemonId) != null) {
                player.sendMessage(
                    net.minecraft.text.Text.literal("This Pokemon is currently managed by a linked Command Post.")
                        .formatted(net.minecraft.util.Formatting.YELLOW),
                    true
                )
                return
            }
            val stack = tagInventory.getStack(0)
            if (!stack.isEmpty && stack.item is TagItem) {
                val tagItem = stack.item as TagItem
                val filter = TagItem.getFilter(stack, player.world.registryManager)
                val boundPos = TagItem.getBoundPos(stack)
                val augments = readAugmentsFromSlots()
                val settings = TagItem.getSettings(stack)

                val oldTag = TagAssignmentManager.get(currentPokemonId)
                val newTag = TagInstance(tagItem.tagType, filter, boundPos, augments, settings)

                // If tag type changed or was re-configured, clean up old work state
                if (oldTag != null && (oldTag.type != newTag.type || oldTag != newTag)) {
                    TagExecutionEngine.cleanup(currentPokemonId, player.world, player.blockPos)
                }

                TagAssignmentManager.assign(currentPokemonId, newTag)
                CobblePalsSaveData.markDirty(player.world as net.minecraft.server.world.ServerWorld)
            } else {
                // Tag removed — full cleanup of active work
                val hadTag = TagAssignmentManager.remove(currentPokemonId)
                if (hadTag != null) {
                    TagExecutionEngine.cleanup(currentPokemonId, player.world, player.blockPos)
                    CobblePalsSaveData.markDirty(player.world as net.minecraft.server.world.ServerWorld)
                }
            }
        }
    }

    private fun readAugmentsFromSlots(): AugmentSet {
        val entries = mutableListOf<AugmentSet.AugmentEntry>()
        for (i in 0 until AUGMENT_SLOT_COUNT) {
            val stack = augmentInventory.getStack(i)
            if (stack.isEmpty) continue
            val augItem = stack.item as? AugmentItem ?: continue
            entries.add(AugmentSet.AugmentEntry(augItem.augmentType, stack.count.coerceIn(1, augItem.augmentType.maxLevel)))
        }
        return AugmentSet(entries)
    }

    private fun collapseAugmentsForDisplay(augmentSet: AugmentSet): List<AugmentSet.AugmentEntry> =
        AugmentType.entries.mapNotNull { type ->
            val level = augmentSet.getLevel(type)
            if (level <= 0) null else AugmentSet.AugmentEntry(type, level)
        }

    private fun insertAugmentLevels(stack: ItemStack) {
        val augmentItem = stack.item as? AugmentItem ?: return
        val augmentType = augmentItem.augmentType
        var remainingCapacity = augmentType.maxLevel - currentAugmentLevel(augmentType)
        if (remainingCapacity <= 0) return

        for (slotIndex in AUGMENT_SLOT_START until AUGMENT_SLOT_END) {
            if (remainingCapacity <= 0 || stack.isEmpty) return
            val slot = slots[slotIndex]
            val slotStack = slot.stack
            if (slotStack.isEmpty || !ItemStack.areItemsAndComponentsEqual(slotStack, stack)) continue

            val transferAmount = minOf(slot.maxItemCount - slotStack.count, remainingCapacity, stack.count)
            if (transferAmount <= 0) continue

            slotStack.increment(transferAmount)
            stack.decrement(transferAmount)
            remainingCapacity -= transferAmount
            slot.markDirty()
        }

        for (slotIndex in AUGMENT_SLOT_START until AUGMENT_SLOT_END) {
            if (remainingCapacity <= 0 || stack.isEmpty) return
            val slot = slots[slotIndex]
            if (!slot.stack.isEmpty) continue

            val transferAmount = minOf(slot.maxItemCount, remainingCapacity, stack.count)
            if (transferAmount <= 0) continue

            slot.stack = stack.split(transferAmount)
            remainingCapacity -= transferAmount
            slot.markDirty()
        }
    }

    private fun currentAugmentLevel(type: AugmentType): Int {
        var total = 0
        for (i in 0 until AUGMENT_SLOT_COUNT) {
            val stack = augmentInventory.getStack(i)
            val augmentItem = stack.item as? AugmentItem ?: continue
            if (augmentItem.augmentType != type) continue
            total += stack.count
        }
        return total.coerceAtMost(type.maxLevel)
    }
}
