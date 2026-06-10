package com.tonnomdeved.volt.data.hibernation

import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsEstimatorTest {

    private fun score(total: Int) = NocivityBreakdown(
        packageName = "com.x", total = total,
        inactivity = 0, backgroundRatio = 0, wakeups = null,
        networkBackground = 0, batteryImpact = null,
        daysSinceLastUse = 0, backgroundBytesLast7d = 0L, foregroundTimeLast7dMs = 0L
    )

    @Test fun `empty list yields zero savings`() {
        val s = SavingsEstimator.estimate(emptyList())
        assertEquals(0.0, s.dailyPercent, 0.001)
        assertEquals(0, s.dailyMah)
        assertEquals(0, s.appCount)
    }

    @Test fun `OFF level apps are ignored`() {
        val s = SavingsEstimator.estimate(listOf(HibernationLevel.OFF to score(90)))
        assertEquals(0, s.appCount)
        assertEquals(0.0, s.dailyPercent, 0.001)
    }

    @Test fun `higher score yields higher savings`() {
        val low  = SavingsEstimator.estimate(listOf(HibernationLevel.SOFT to score(30)))
        val high = SavingsEstimator.estimate(listOf(HibernationLevel.SOFT to score(90)))
        assertTrue(high.dailyPercent > low.dailyPercent)
    }

    @Test fun `HARD level saves more than SOFT for same score`() {
        val soft = SavingsEstimator.estimate(listOf(HibernationLevel.SOFT to score(80)))
        val hard = SavingsEstimator.estimate(listOf(HibernationLevel.HARD to score(80)))
        assertTrue(hard.dailyPercent > soft.dailyPercent)
    }

    @Test fun `savings are capped at 30 percent`() {
        val many = (1..50).map { HibernationLevel.HARD to score(100) }
        val s = SavingsEstimator.estimate(many)
        assertTrue(s.dailyPercent <= 30.0)
    }

    @Test fun `mah is positive when apps are hibernated`() {
        val s = SavingsEstimator.estimate(listOf(HibernationLevel.MEDIUM to score(70)))
        assertTrue(s.dailyMah > 0)
        assertEquals(1, s.appCount)
    }
}
