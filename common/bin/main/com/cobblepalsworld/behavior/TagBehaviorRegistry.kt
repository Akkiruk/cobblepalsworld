package com.cobblepalsworld.behavior

import com.cobblepalsworld.tag.TagType

object TagBehaviorRegistry {
    private val behaviors = mutableMapOf<TagType, TagBehavior>()

    fun register(behavior: TagBehavior) {
        behaviors[behavior.tagType] = behavior
    }

    fun get(type: TagType): TagBehavior? = behaviors[type]
}
