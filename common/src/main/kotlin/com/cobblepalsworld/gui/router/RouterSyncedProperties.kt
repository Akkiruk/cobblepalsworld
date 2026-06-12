package com.cobblepalsworld.gui.router

import com.cobblepalsworld.router.RouterBlockEntity
import net.minecraft.screen.PropertyDelegate

/**
 * Typed declaration for the Command Post/Router integer properties synced through [PropertyDelegate].
 *
 * Wire order is intentionally preserved as:
 * linked flag, roster count, assigned count, active count,
 * module-assigned flags for module slots 0..8, module-active flags for module slots 0..8,
 * and router position components x/y/z.
 *
 * To add a synced property safely, declare it in the layout block below at the exact wire position
 * required by the protocol. Add scalar values with [LayoutBuilder.scalar] and contiguous blocks with
 * [LayoutBuilder.array], then update the server-side `when` in `RouterBlockEntity` to provide the
 * value. Client code should only read through these named accessors so indices stay centralized here.
 */
object RouterSyncedProperties {
    sealed class Entry protected constructor(
        val index: Int,
        val id: String
    )

    class Scalar internal constructor(index: Int, id: String) : Entry(index, id) {
        fun get(data: PropertyDelegate): Int = data.get(index)
        fun getBoolean(data: PropertyDelegate): Boolean = get(data) != 0
    }

    class ArrayElement internal constructor(
        index: Int,
        id: String,
        internal val blockId: String,
        val offset: Int
    ) : Entry(index, id)

    enum class PosComponent { X, Y, Z }

    class ArrayBlock internal constructor(
        private val id: String,
        private val elements: List<ArrayElement>
    ) {
        val size: Int = elements.size

        fun matches(element: ArrayElement): Boolean = element.blockId == id
        fun element(offset: Int): ArrayElement? = elements.getOrNull(offset)
        fun get(data: PropertyDelegate, offset: Int): Int = element(offset)?.let { data.get(it.index) } ?: 0
        fun get(data: PropertyDelegate, component: PosComponent): Int = get(data, component.ordinal)
        fun getBoolean(data: PropertyDelegate, offset: Int): Boolean = get(data, offset) != 0
    }

    class LayoutBuilder internal constructor() {
        private val mutableEntries = mutableListOf<Entry>()
        val entries: List<Entry> get() = mutableEntries

        fun scalar(id: String): Scalar {
            return Scalar(mutableEntries.size, id).also(mutableEntries::add)
        }

        fun array(id: String, size: Int): ArrayBlock {
            val elements = List(size) { offset ->
                ArrayElement(mutableEntries.size + offset, "$id[$offset]", id, offset)
            }
            mutableEntries += elements
            return ArrayBlock(id, elements)
        }
    }

    private val layout = LayoutBuilder()

    val LINKED: Scalar = layout.scalar("linked")
    val ROSTER_COUNT: Scalar = layout.scalar("roster_count")
    val ASSIGNED_COUNT: Scalar = layout.scalar("assigned_count")
    val ACTIVE_COUNT: Scalar = layout.scalar("active_count")
    val MODULE_ASSIGNED: ArrayBlock = layout.array("module_assigned", RouterBlockEntity.MODULE_SLOT_COUNT)
    val MODULE_ACTIVE: ArrayBlock = layout.array("module_active", RouterBlockEntity.MODULE_SLOT_COUNT)
    val POS_COMPONENT: ArrayBlock = layout.array("pos_component", 3)

    private val entries: List<Entry> = layout.entries.toList()
    val totalCount: Int = entries.size

    fun byIndex(index: Int): Entry? = entries.getOrNull(index)
}
