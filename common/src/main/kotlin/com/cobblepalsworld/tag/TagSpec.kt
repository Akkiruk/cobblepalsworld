package com.cobblepalsworld.tag

import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.util.math.BlockPos

data class TagSpec(
    val type: TagType,
    val filter: TagFilter = TagFilter.EMPTY,
    val boundPos: BlockPos? = null,
    val boundArea: BoundArea? = null,
    val settings: TagSettings = TagSettings.EMPTY
) {
    fun toTagInstance(augments: AugmentSet = AugmentSet.EMPTY, controllerPos: BlockPos? = null): TagInstance {
        return TagInstance(
            type = type,
            filter = filter,
            boundPos = boundPos,
            boundArea = boundArea,
            controllerPos = controllerPos,
            augments = augments,
            settings = settings
        )
    }
}

fun TagInstance.toSpec(): TagSpec = TagSpec(
    type = type,
    filter = filter,
    boundPos = boundPos,
    boundArea = boundArea,
    settings = settings
)
