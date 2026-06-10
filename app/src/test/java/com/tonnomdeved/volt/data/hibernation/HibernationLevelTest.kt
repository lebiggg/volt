package com.tonnomdeved.volt.data.hibernation

import android.app.usage.UsageStatsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM purs pour [HibernationLevel].
 * Aucune dépendance Android → exécutables en `./gradlew :app:testDebugUnitTest`.
 */
class HibernationLevelTest {

    @Test fun `OFF maps to ACTIVE bucket`() {
        assertEquals(UsageStatsManager.STANDBY_BUCKET_ACTIVE, HibernationLevel.OFF.standbyBucket)
    }

    @Test fun `SOFT MEDIUM HARD all map to RESTRICTED bucket`() {
        listOf(HibernationLevel.SOFT, HibernationLevel.MEDIUM, HibernationLevel.HARD).forEach {
            assertEquals(UsageStatsManager.STANDBY_BUCKET_RESTRICTED, it.standbyBucket)
        }
    }

    @Test fun `only MEDIUM and HARD need shizuku`() {
        assertFalse(HibernationLevel.OFF.needsShizuku)
        assertFalse(HibernationLevel.SOFT.needsShizuku)
        assertTrue(HibernationLevel.MEDIUM.needsShizuku)
        assertTrue(HibernationLevel.HARD.needsShizuku)
    }

    @Test fun `only HARD does periodic forceStop`() {
        assertTrue(HibernationLevel.HARD.periodicForceStop)
        assertFalse(HibernationLevel.MEDIUM.periodicForceStop)
        assertFalse(HibernationLevel.SOFT.periodicForceStop)
        assertFalse(HibernationLevel.OFF.periodicForceStop)
    }

    @Test fun `fallbackWithoutShizuku downgrades MEDIUM and HARD to SOFT`() {
        assertEquals(HibernationLevel.SOFT, HibernationLevel.MEDIUM.fallbackWithoutShizuku())
        assertEquals(HibernationLevel.SOFT, HibernationLevel.HARD.fallbackWithoutShizuku())
    }

    @Test fun `fallbackWithoutShizuku is identity for OFF and SOFT`() {
        assertEquals(HibernationLevel.OFF, HibernationLevel.OFF.fallbackWithoutShizuku())
        assertEquals(HibernationLevel.SOFT, HibernationLevel.SOFT.fallbackWithoutShizuku())
    }

    @Test fun `isActive is true for non-OFF levels`() {
        assertFalse(HibernationLevel.OFF.isActive())
        assertTrue(HibernationLevel.SOFT.isActive())
        assertTrue(HibernationLevel.MEDIUM.isActive())
        assertTrue(HibernationLevel.HARD.isActive())
    }

    @Test fun `fromName decodes valid names`() {
        assertEquals(HibernationLevel.SOFT,   HibernationLevel.fromName("SOFT"))
        assertEquals(HibernationLevel.MEDIUM, HibernationLevel.fromName("MEDIUM"))
        assertEquals(HibernationLevel.HARD,   HibernationLevel.fromName("HARD"))
        assertEquals(HibernationLevel.OFF,    HibernationLevel.fromName("OFF"))
    }

    @Test fun `fromName falls back to OFF for unknown or null`() {
        assertEquals(HibernationLevel.OFF, HibernationLevel.fromName(null))
        assertEquals(HibernationLevel.OFF, HibernationLevel.fromName(""))
        assertEquals(HibernationLevel.OFF, HibernationLevel.fromName("CRYO"))
    }
}
