package com.cobblepalsworld.tag

import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.util.math.BlockPos

/**
 * Runtime representation of a tag assigned to a Pokémon.
 * Holds the tag type, filter config, bound position, and augments.
 */
data class TagInstance(
    val type: TagType,
    val filter: TagFilter = TagFilter.EMPTY,
    val boundPos: BlockPos? = null,
    val boundArea: BoundArea? = null,
    val controllerPos: BlockPos? = null,
    val augments: AugmentSet = AugmentSet.EMPTY,
    val settings: TagSettings = TagSettings.EMPTY
)
