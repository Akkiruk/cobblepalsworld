package com.cobblepalsworld.tag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TagTypeDefinitionsTest {

    @Test
    fun `every tag type resolves a definition with a matching id`() {
        for (type in TagType.entries) {
            val definition = TagTypeDefinitions.get(type)
            assertEquals(type.id, definition.id, "definition id must match the role's persistent id")
        }
    }

    @Test
    fun `fromId round trips every persistent id`() {
        for (type in TagType.entries) {
            assertEquals(type, TagType.fromId(type.id))
        }
        assertNull(TagType.fromId("not_a_role"))
    }

    @Test
    fun `override rejects definitions with a mismatched id`() {
        val foreign = TagTypeDefinitions.get(TagType.VACUUM)
        assertThrows(IllegalArgumentException::class.java) {
            TagTypeDefinitions.override(TagType.BREAKER, foreign)
        }
    }

    @Test
    fun `override replaces a definition and keeps the enum delegating`() {
        val original = TagTypeDefinitions.get(TagType.VACUUM)
        try {
            TagTypeDefinitions.override(TagType.VACUUM, original.copy(supportsTargetList = true))
            assertEquals(true, TagType.VACUUM.supportsTargetList)
        } finally {
            TagTypeDefinitions.override(TagType.VACUUM, original)
        }
    }

    @Test
    fun `binding support is derived from the binding mode`() {
        for (type in TagType.entries) {
            assertEquals(type.bindingMode != BindingMode.NONE, type.supportsBinding)
        }
    }
}
