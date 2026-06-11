package com.cobblepalsworld.behavior

import com.cobblepalsworld.tag.TagType

object TagBehaviorRegistry {
    private val behaviors = mutableMapOf<TagType, TagBehavior>()

    fun register(behavior: TagBehavior) {
        val existing = behaviors.put(behavior.tagType, behavior)
        if (existing != null && existing !== behavior) {
            com.cobblepalsworld.CobblePalsWorld.LOGGER.warn(
                "Tag behavior for '{}' was replaced: {} -> {}",
                behavior.tagType.id, existing.javaClass.name, behavior.javaClass.name
            )
        }
    }

    fun get(type: TagType): TagBehavior? = behaviors[type]

    fun all(): Collection<TagBehavior> = behaviors.values
}
