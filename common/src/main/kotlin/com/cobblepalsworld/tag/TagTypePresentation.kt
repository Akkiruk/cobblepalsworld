package com.cobblepalsworld.tag

import java.util.Locale

enum class TagRoleFamily(val label: String) {
    Gathering("Gather"),
    Logistics("Logistics"),
    Defense("Defense"),
    Interaction("Interact"),
    Care("Care")
}

object TagTypePresentation {
    fun roleLabel(tagType: TagType): String {
        return tagType.name
            .lowercase(Locale.ROOT)
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
    }

    fun familyOf(tagType: TagType): TagRoleFamily {
        return when (tagType) {
            TagType.BREAKER, TagType.HARVESTER, TagType.VACUUM -> TagRoleFamily.Gathering
            TagType.COURIER, TagType.PULLER, TagType.STASHER, TagType.DROPPER, TagType.VOID -> TagRoleFamily.Logistics
            TagType.GUARDIAN -> TagRoleFamily.Defense
            TagType.ACTIVATOR -> TagRoleFamily.Interaction
            TagType.SHEPHERD -> TagRoleFamily.Care
        }
    }

    fun bindingLabel(tagType: TagType): String {
        return when (tagType.bindingMode) {
            BindingMode.CONTAINER -> "Bound Container"
            BindingMode.POSITION -> when (tagType) {
                TagType.BREAKER -> "Bound Block"
                TagType.ACTIVATOR -> "Bound Target"
                else -> "Bound Position"
            }
            BindingMode.AREA -> "Bound Box"
            BindingMode.NONE -> "Bound"
        }
    }
}