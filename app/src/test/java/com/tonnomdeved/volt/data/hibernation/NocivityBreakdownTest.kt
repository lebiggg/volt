package com.tonnomdeved.volt.data.hibernation

import com.tonnomdeved.volt.data.hibernation.nocivity.NocivityBreakdown
import org.junit.Assert.assertEquals
import org.junit.Test

class NocivityBreakdownTest {

    private fun breakdown(total: Int) = NocivityBreakdown(
        packageName = "com.example",
        total = total,
        inactivity = 0, backgroundRatio = 0,
        wakeups = null, networkBackground = 0, batteryImpact = null,
        daysSinceLastUse = 0, backgroundBytesLast7d = 0L, foregroundTimeLast7dMs = 0L
    )

    @Test fun `severity CALM for score below 30`() {
        assertEquals(NocivityBreakdown.Severity.CALM, breakdown(0).severity)
        assertEquals(NocivityBreakdown.Severity.CALM, breakdown(29).severity)
    }

    @Test fun `severity MODERATE for score 30-59`() {
        assertEquals(NocivityBreakdown.Severity.MODERATE, breakdown(30).severity)
        assertEquals(NocivityBreakdown.Severity.MODERATE, breakdown(59).severity)
    }

    @Test fun `severity HIGH for score 60-84`() {
        assertEquals(NocivityBreakdown.Severity.HIGH, breakdown(60).severity)
        assertEquals(NocivityBreakdown.Severity.HIGH, breakdown(84).severity)
    }

    @Test fun `severity CRITICAL for score 85+`() {
        assertEquals(NocivityBreakdown.Severity.CRITICAL, breakdown(85).severity)
        assertEquals(NocivityBreakdown.Severity.CRITICAL, breakdown(100).severity)
    }
}
