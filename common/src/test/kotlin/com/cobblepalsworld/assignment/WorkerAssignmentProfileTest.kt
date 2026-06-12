package com.cobblepalsworld.assignment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerAssignmentProfileTest {

    @Test
    fun `default profile is general with fallback`() {
        val profile = WorkerAssignmentProfile()
        assertTrue(profile.isDefault())
        assertEquals("General", profile.label())
    }

    @Test
    fun `customized profiles are not default`() {
        assertFalse(WorkerAssignmentProfile(mode = WorkerAssignmentMode.RESERVED).isDefault())
        assertFalse(WorkerAssignmentProfile(mode = WorkerAssignmentMode.PREFERRED).isDefault())
        assertFalse(WorkerAssignmentProfile(allowFallback = false).isDefault())
    }

    @Test
    fun `labels reflect mode and fallback combinations`() {
        assertEquals("Reserved", WorkerAssignmentProfile(mode = WorkerAssignmentMode.RESERVED).label())
        assertEquals("Reserved", WorkerAssignmentProfile(mode = WorkerAssignmentMode.RESERVED, allowFallback = false).label())
        assertEquals("Preferred", WorkerAssignmentProfile(mode = WorkerAssignmentMode.PREFERRED).label())
        assertEquals("Restricted", WorkerAssignmentProfile(mode = WorkerAssignmentMode.PREFERRED, allowFallback = false).label())
        assertEquals("Strict", WorkerAssignmentProfile(allowFallback = false).label())
    }

    @Test
    fun `fromOrdinal tolerates out of range values`() {
        assertEquals(WorkerAssignmentMode.GENERAL, WorkerAssignmentMode.fromOrdinal(-1))
        assertEquals(WorkerAssignmentMode.GENERAL, WorkerAssignmentMode.fromOrdinal(99))
        assertEquals(WorkerAssignmentMode.PREFERRED, WorkerAssignmentMode.fromOrdinal(WorkerAssignmentMode.PREFERRED.ordinal))
        assertEquals(WorkerAssignmentMode.RESERVED, WorkerAssignmentMode.fromOrdinal(WorkerAssignmentMode.RESERVED.ordinal))
    }
}
