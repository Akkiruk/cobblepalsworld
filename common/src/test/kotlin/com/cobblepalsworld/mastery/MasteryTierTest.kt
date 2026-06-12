package com.cobblepalsworld.mastery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MasteryTierTest {

    @Test
    fun `forJobs maps job counts to tier boundaries`() {
        assertEquals(MasteryTier.NOVICE, MasteryTier.forJobs(0L))
        assertEquals(MasteryTier.NOVICE, MasteryTier.forJobs(24L))
        assertEquals(MasteryTier.APPRENTICE, MasteryTier.forJobs(25L))
        assertEquals(MasteryTier.APPRENTICE, MasteryTier.forJobs(99L))
        assertEquals(MasteryTier.ADEPT, MasteryTier.forJobs(100L))
        assertEquals(MasteryTier.EXPERT, MasteryTier.forJobs(300L))
        assertEquals(MasteryTier.MASTER, MasteryTier.forJobs(750L))
        assertEquals(MasteryTier.MASTER, MasteryTier.forJobs(Long.MAX_VALUE))
    }

    @Test
    fun `forJobs never rewards negative job counts beyond novice`() {
        assertEquals(MasteryTier.NOVICE, MasteryTier.forJobs(-1L))
    }

    @Test
    fun `nextTier chains through every tier and ends at master`() {
        assertEquals(MasteryTier.APPRENTICE, MasteryTier.NOVICE.nextTier)
        assertEquals(MasteryTier.ADEPT, MasteryTier.APPRENTICE.nextTier)
        assertEquals(MasteryTier.EXPERT, MasteryTier.ADEPT.nextTier)
        assertEquals(MasteryTier.MASTER, MasteryTier.EXPERT.nextTier)
        assertNull(MasteryTier.MASTER.nextTier)
    }

    @Test
    fun `tier perks improve monotonically`() {
        val tiers = MasteryTier.entries
        for (i in 1 until tiers.size) {
            assertTrue(tiers[i].requiredJobs > tiers[i - 1].requiredJobs, "requiredJobs must increase")
            assertTrue(tiers[i].cooldownMultiplier <= tiers[i - 1].cooldownMultiplier, "cooldown must not regress")
            assertTrue(tiers[i].bonusRange >= tiers[i - 1].bonusRange, "bonus range must not regress")
        }
    }
}
