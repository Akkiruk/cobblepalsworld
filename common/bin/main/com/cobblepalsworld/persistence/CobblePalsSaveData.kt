package com.cobblepalsworld.persistence

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.augment.AugmentSerializer
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterSerializer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import java.util.UUID

class CobblePalsSaveData : PersistentState() {

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): NbtCompound {
        // Save tag assignments
        val assignmentsNbt = NbtCompound()
        for ((uuid, tag) in getAllAssignments()) {
            val tagNbt = NbtCompound()
            tagNbt.putString("Type", tag.type.id)
            tagNbt.put("Filter", FilterSerializer.toNbt(tag.filter, registries))
            tagNbt.put("Augments", AugmentSerializer.toNbt(tag.augments))
            tag.boundPos?.let { pos ->
                tagNbt.putInt("BoundX", pos.x)
                tagNbt.putInt("BoundY", pos.y)
                tagNbt.putInt("BoundZ", pos.z)
            }
            assignmentsNbt.put(uuid.toString(), tagNbt)
        }
        nbt.put("Assignments", assignmentsNbt)

        // Save inventories
        val inventoriesNbt = NbtCompound()
        for ((uuid, inventory) in getAllInventories()) {
            val invNbt = NbtCompound()
            invNbt.putInt("Size", inventory.size())
            val itemsNbt = NbtList()
            for (slot in 0 until inventory.size()) {
                val stack = inventory.getStack(slot)
                if (!stack.isEmpty) {
                    val slotNbt = NbtCompound()
                    slotNbt.putByte("Slot", slot.toByte())
                    slotNbt.put("Item", stack.encodeAllowEmpty(registries))
                    itemsNbt.add(slotNbt)
                }
            }
            invNbt.put("Items", itemsNbt)
            inventoriesNbt.put(uuid.toString(), invNbt)
        }
        nbt.put("Inventories", inventoriesNbt)

        return nbt
    }

    private fun getAllAssignments(): Map<UUID, TagInstance> {
        val result = mutableMapOf<UUID, TagInstance>()
        // Iterate TagAssignmentManager's assignments
        // We need access — let TagAssignmentManager expose a snapshot
        TagAssignmentManager.forEach { uuid, tag -> result[uuid] = tag }
        return result
    }

    private fun getAllInventories(): Map<UUID, PokemonInventory> {
        val result = mutableMapOf<UUID, PokemonInventory>()
        InventoryManager.forEach { uuid, inv -> result[uuid] = inv }
        return result
    }

    companion object {
        private const val DATA_ID = "cobblepalsworld"

        fun fromNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): CobblePalsSaveData {
            val data = CobblePalsSaveData()

            // Load assignments
            if (nbt.contains("Assignments")) {
                val assignmentsNbt = nbt.getCompound("Assignments")
                for (key in assignmentsNbt.keys) {
                    try {
                        val uuid = UUID.fromString(key)
                        val tagNbt = assignmentsNbt.getCompound(key)
                        val type = TagType.fromId(tagNbt.getString("Type")) ?: continue
                        val filter = FilterSerializer.fromNbt(tagNbt.getCompound("Filter"), registries)
                        val augments = if (tagNbt.contains("Augments")) {
                            AugmentSerializer.fromNbt(tagNbt.getCompound("Augments"))
                        } else {
                            com.cobblepalsworld.augment.AugmentSet.EMPTY
                        }
                        val boundPos = if (tagNbt.contains("BoundX")) {
                            BlockPos(tagNbt.getInt("BoundX"), tagNbt.getInt("BoundY"), tagNbt.getInt("BoundZ"))
                        } else null
                        TagAssignmentManager.assign(uuid, TagInstance(type, filter, boundPos, augments))
                    } catch (e: Exception) {
                        CobblePalsWorld.LOGGER.warn("Failed to load assignment for $key", e)
                    }
                }
            }

            // Load inventories
            if (nbt.contains("Inventories")) {
                val inventoriesNbt = nbt.getCompound("Inventories")
                for (key in inventoriesNbt.keys) {
                    try {
                        val uuid = UUID.fromString(key)
                        val invNbt = inventoriesNbt.getCompound(key)
                        val size = invNbt.getInt("Size")
                        val inventory = PokemonInventory(uuid, size)
                        val itemsNbt = invNbt.getList("Items", 10) // 10 = NbtCompound type
                        for (i in 0 until itemsNbt.size) {
                            val slotNbt = itemsNbt.getCompound(i)
                            val slot = slotNbt.getByte("Slot").toInt()
                            val stack = ItemStack.fromNbt(registries, slotNbt.get("Item")!!)
                            stack.ifPresent { inventory.setStack(slot, it) }
                        }
                        InventoryManager.put(uuid, inventory)
                    } catch (e: Exception) {
                        CobblePalsWorld.LOGGER.warn("Failed to load inventory for $key", e)
                    }
                }
            }

            return data
        }

        fun getType(): Type<CobblePalsSaveData> {
            return Type(
                { CobblePalsSaveData() },
                { nbt, registries -> fromNbt(nbt, registries) },
                null
            )
        }

        fun get(world: ServerWorld): CobblePalsSaveData {
            return world.persistentStateManager.getOrCreate(getType(), DATA_ID)
        }

        fun markDirty(world: ServerWorld) {
            get(world).markDirty()
        }
    }
}
