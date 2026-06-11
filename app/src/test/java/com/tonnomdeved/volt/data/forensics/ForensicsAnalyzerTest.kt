package com.tonnomdeved.volt.data.forensics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicsAnalyzerTest {

    @Test fun `inert app scores zero`() {
        assertEquals(0, ForensicsAnalyzer.computeImpact(0, 0L, 0))
    }

    @Test fun `null wakeups contribute nothing`() {
        val withNull = ForensicsAnalyzer.computeImpact(0, 0L, null)
        assertEquals(0, withNull)
    }

    @Test fun `heavy wakeups dominate the score`() {
        val score = ForensicsAnalyzer.computeImpact(0, 0L, 200)
        assertEquals(50, score)
    }

    @Test fun `more wakeups means higher score`() {
        val low  = ForensicsAnalyzer.computeImpact(0, 0L, 3)
        val high = ForensicsAnalyzer.computeImpact(0, 0L, 60)
        assertTrue(high > low)
    }

    @Test fun `combined signals add up and clamp at 100`() {
        val mb100 = 100L * 1024 * 1024
        val score = ForensicsAnalyzer.computeImpact(50, mb100, 200)
        assertTrue(score in 90..100)
    }

    @Test fun `foreground events alone contribute`() {
        assertTrue(ForensicsAnalyzer.computeImpact(20, 0L, null) > 0)
    }

    @Test fun `background network alone contributes`() {
        val mb20 = 20L * 1024 * 1024
        assertTrue(ForensicsAnalyzer.computeImpact(0, mb20, null) > 0)
    }

    // --- UID token decoding (real Android 16 dumpsys format) ---

    @Test fun `system uid token is numeric`() {
        assertEquals(1000, ForensicsAnalyzer.parseUidToken("1000"))
    }

    @Test fun `app uid token decodes user 0`() {
        assertEquals(10129, ForensicsAnalyzer.parseUidToken("u0a129"))
        assertEquals(10078, ForensicsAnalyzer.parseUidToken("u0a78"))
    }

    @Test fun `app uid token decodes secondary user`() {
        assertEquals(1010005, ForensicsAnalyzer.parseUidToken("u10a5"))
    }

    @Test fun `garbage token returns null`() {
        assertEquals(null, ForensicsAnalyzer.parseUidToken("xyz"))
        assertEquals(null, ForensicsAnalyzer.parseUidToken(""))
    }
}
