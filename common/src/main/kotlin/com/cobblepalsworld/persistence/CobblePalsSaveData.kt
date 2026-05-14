package com.cobblepalsworld.persistence

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.augment.AugmentSerializer
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.crew.CommandPostCrewBinding
import com.cobblepalsworld.crew.CommandPostCrewMember
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.pasture.ControllerBinding
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.pasture.WorkerAssignmentMode
import com.cobblepalsworld.pasture.WorkerAssignmentProfile
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagSettings
import com.cobblepalsworld.tag.TagSettingsSerializer
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterSerializer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class AssignmentRecord(
    val tag: TagInstance,
    val pastureBinding: com.cobblepalsworld.pasture.PastureBinding?,
    val controllerBinding: ControllerBinding?,
    val assignmentProfile: WorkerAssignmentProfile
)

class CobblePalsSaveData : PersistentState() {

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): NbtCompound {
        // Save tag assignments
        val assignmentsNbt = NbtCompound()
        for ((uuid, record) in getAllAssignments()) {
            val (tag, pastureBinding, controllerBinding, assignmentProfile) = record
            val tagNbt = NbtCompound()
            tagNbt.putString("Type", tag.type.id)
            tagNbt.put("Filter", FilterSerializer.toNbt(tag.filter, registries))
            tagNbt.put("Augments", AugmentSerializer.toNbt(tag.augments))
            tagNbt.put("Settings", TagSettingsSerializer.toNbt(tag.settings))
            tagNbt.putInt("AssignmentMode", assignmentProfile.mode.ordinal)
            tagNbt.putBoolean("AllowFallback", assignmentProfile.allowFallback)
            tag.boundPos?.let { pos ->
                tagNbt.putInt("BoundX", pos.x)
                tagNbt.putInt("BoundY", pos.y)
                tagNbt.putInt("BoundZ", pos.z)
            }
            tag.boundArea?.let { area ->
                tagNbt.putInt("AreaMinX", area.min.x)
                tagNbt.putInt("AreaMinY", area.min.y)
                tagNbt.putInt("AreaMinZ", area.min.z)
                tagNbt.putInt("AreaMaxX", area.max.x)
                tagNbt.putInt("AreaMaxY", area.max.y)
                tagNbt.putInt("AreaMaxZ", area.max.z)
            }
            pastureBinding?.let { binding ->
                tagNbt.putString("PastureDimension", binding.dimensionId)
                tagNbt.putInt("PastureX", binding.pos.x)
                tagNbt.putInt("PastureY", binding.pos.y)
                tagNbt.putInt("PastureZ", binding.pos.z)
            }
            controllerBinding?.let { binding ->
                tagNbt.putString("ControllerDimension", binding.dimensionId)
                tagNbt.putInt("ControllerX", binding.pos.x)
                tagNbt.putInt("ControllerY", binding.pos.y)
                tagNbt.putInt("ControllerZ", binding.pos.z)
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

        val commandPostCrewsNbt = NbtList()
        CommandPostCrewManager.forEachPost { binding, members ->
            if (members.isEmpty()) return@forEachPost
            val postNbt = NbtCompound()
            postNbt.putString("Dimension", binding.dimensionId)
            postNbt.putInt("X", binding.pos.x)
            postNbt.putInt("Y", binding.pos.y)
            postNbt.putInt("Z", binding.pos.z)
            val idsNbt = NbtList()
            members.forEach { member ->
                val idNbt = NbtCompound()
                idNbt.putUuid("PokemonId", member.pokemonId)
                idNbt.putUuid("OwnerUuid", member.ownerUuid)
                idNbt.putString("SourceType", member.sourceType)
                idNbt.putInt("BoxIndex", member.boxIndex)
                idNbt.putInt("SlotIndex", member.slotIndex)
                idNbt.putString("DisplayName", member.displayName)
                idNbt.putString("Species", member.species)
                idNbt.putInt("Level", member.level)
                idsNbt.add(idNbt)
            }
            postNbt.put("Pokemon", idsNbt)
            commandPostCrewsNbt.add(postNbt)
        }
        nbt.put("CommandPostCrews", commandPostCrewsNbt)

        return nbt
    }

    private fun getAllAssignments(): Map<UUID, AssignmentRecord> {
        val result = mutableMapOf<UUID, AssignmentRecord>()
        TagAssignmentManager.forEachRecord { uuid, tag, pastureBinding, controllerBinding, assignmentProfile ->
            result[uuid] = AssignmentRecord(tag, pastureBinding, controllerBinding, assignmentProfile)
        }
        return result
    }

    private fun getAllInventories(): Map<UUID, PokemonInventory> {
        val result = mutableMapOf<UUID, PokemonInventory>()
        InventoryManager.forEach { uuid, inv -> result[uuid] = inv }
        return result
    }

    companion object {
        private const val DATA_ID = "cobblepalsworld"
        private val initializedServers = ConcurrentHashMap.newKeySet<MinecraftServer>()

        fun fromNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): CobblePalsSaveData {
            val data = CobblePalsSaveData()

            TagAssignmentManager.clear()
            CommandPostCrewManager.clear()
            InventoryManager.clear()
            StateManager.clear()
            ClaimManager.clear()

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
                        val settings = if (tagNbt.contains("Settings")) {
                            TagSettingsSerializer.fromNbt(tagNbt.getCompound("Settings"))
                        } else {
                            TagSettings.EMPTY
                        }
                        val boundPos = if (tagNbt.contains("BoundX")) {
                            BlockPos(tagNbt.getInt("BoundX"), tagNbt.getInt("BoundY"), tagNbt.getInt("BoundZ"))
                        } else null
                        val boundArea = if (tagNbt.contains("AreaMinX") && tagNbt.contains("AreaMaxX")) {
                            com.cobblepalsworld.tag.BoundArea(
                                BlockPos(tagNbt.getInt("AreaMinX"), tagNbt.getInt("AreaMinY"), tagNbt.getInt("AreaMinZ")),
                                BlockPos(tagNbt.getInt("AreaMaxX"), tagNbt.getInt("AreaMaxY"), tagNbt.getInt("AreaMaxZ"))
                            )
                        } else null
                        val tag = TagInstance(
                            type = type,
                            filter = filter,
                            boundPos = boundPos,
                            boundArea = boundArea,
                            augments = augments,
                            settings = settings
                        )
                        if (tagNbt.contains("ControllerDimension")) {
                            val controllerPos = BlockPos(
                                tagNbt.getInt("ControllerX"),
                                tagNbt.getInt("ControllerY"),
                                tagNbt.getInt("ControllerZ")
                            )
                            TagAssignmentManager.assignFromController(uuid, tag, tagNbt.getString("ControllerDimension"), controllerPos)
                        } else {
                            TagAssignmentManager.assign(uuid, tag)
                        }
                        if (tagNbt.contains("PastureDimension")) {
                            val pasturePos = BlockPos(
                                tagNbt.getInt("PastureX"),
                                tagNbt.getInt("PastureY"),
                                tagNbt.getInt("PastureZ")
                            )
                            TagAssignmentManager.associateWithPasture(uuid, tagNbt.getString("PastureDimension"), pasturePos)
                        }
                        TagAssignmentManager.updateProfile(
                            pokemonId = uuid,
                            mode = WorkerAssignmentMode.fromOrdinal(tagNbt.getInt("AssignmentMode")),
                            allowFallback = if (tagNbt.contains("AllowFallback")) tagNbt.getBoolean("AllowFallback") else true
                        )
                    } catch (e: Exception) {
                        CobblePalsWorld.LOGGER.warn("Failed to load assignment for $key", e)
                    }
                }
            }

            if (nbt.contains("CommandPostCrews")) {
                val crewsNbt = nbt.getList("CommandPostCrews", 10)
                for (index in 0 until crewsNbt.size) {
                    try {
                        val postNbt = crewsNbt.getCompound(index)
                        val binding = CommandPostCrewBinding(
                            dimensionId = postNbt.getString("Dimension"),
                            pos = BlockPos(postNbt.getInt("X"), postNbt.getInt("Y"), postNbt.getInt("Z"))
                        )
                        val idsNbt = postNbt.getList("Pokemon", 10)
                        val members = buildList {
                            for (idIndex in 0 until idsNbt.size) {
                                val idNbt = idsNbt.getCompound(idIndex)
                                if (idNbt.containsUuid("PokemonId")) {
                                    add(
                                        CommandPostCrewMember(
                                            pokemonId = idNbt.getUuid("PokemonId"),
                                            ownerUuid = if (idNbt.containsUuid("OwnerUuid")) idNbt.getUuid("OwnerUuid") else UUID(0L, 0L),
                                            binding = binding,
                                            sourceType = idNbt.getString("SourceType").ifBlank { "UNKNOWN" },
                                            boxIndex = idNbt.getInt("BoxIndex"),
                                            slotIndex = idNbt.getInt("SlotIndex"),
                                            displayName = idNbt.getString("DisplayName").ifBlank { "Unknown Pokemon" },
                                            species = idNbt.getString("Species").ifBlank { "unknown" },
                                            level = if (idNbt.contains("Level")) idNbt.getInt("Level") else 0
                                        )
                                    )
                                }
                            }
                        }
                        CommandPostCrewManager.load(members)
                    } catch (e: Exception) {
                        CobblePalsWorld.LOGGER.warn("Failed to load Command Post crew entry", e)
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

            val orphanInventoryIds = mutableListOf<UUID>()
            InventoryManager.forEach { uuid, _ ->
                if (!TagAssignmentManager.has(uuid)) {
                    orphanInventoryIds += uuid
                }
            }
            orphanInventoryIds.forEach(InventoryManager::remove)

            return data
        }

        fun getType(): Type<CobblePalsSaveData> {
            return Type(
                { CobblePalsSaveData() },
                { nbt, registries -> fromNbt(nbt, registries) },
                null
            )
        }

        private fun primaryWorld(server: MinecraftServer): ServerWorld {
            return server.getWorld(World.OVERWORLD)
                ?: server.worlds.firstOrNull()
                ?: error("CobblePals World requires at least one loaded world")
        }

        fun ensureLoaded(server: MinecraftServer) {
            if (initializedServers.add(server)) {
                get(server)
            }
        }

        fun ensureLoaded(world: ServerWorld) {
            ensureLoaded(world.server)
        }

        fun clearLoaded(server: MinecraftServer) {
            initializedServers.remove(server)
        }

        fun get(server: MinecraftServer): CobblePalsSaveData {
            return primaryWorld(server).persistentStateManager.getOrCreate(getType(), DATA_ID)
        }

        fun get(world: ServerWorld): CobblePalsSaveData {
            return get(world.server)
        }

        fun markDirty(server: MinecraftServer) {
            get(server).markDirty()
        }

        fun markDirty(world: ServerWorld) {
            markDirty(world.server)
        }
    }
}
